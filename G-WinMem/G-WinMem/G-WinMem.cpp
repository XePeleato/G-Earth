// G-WinMem.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"
#include <Windows.h>
#include <iostream>
#include <stdlib.h>
#include <string>
#include <iterator>
#include <iphlpapi.h>
#include <psapi.h>
#include <Tlhelp32.h>
#include <WbemIdl.h>
#include <winternl.h>
#include <comdef.h>
#include <vector>
#include <filesystem>

#pragma comment(lib, "iphlpapi.lib")
#pragma comment(lib, "Ws2_32.lib")
#pragma comment(lib, "Ntdll.lib")


class MemoryChunk
{
public:
	LPVOID start;
	SIZE_T size;
};

PVOID GetPebAddress(HANDLE pHandle)
{
	PROCESS_BASIC_INFORMATION pbi;
	NtQueryInformationProcess(pHandle, ProcessBasicInformation, &pbi, sizeof(pbi), nullptr);

	return pbi.PebBaseAddress;
}

bool IsFlashProcess(int pid)
{
	PPROCESS_BASIC_INFORMATION pbi = nullptr;
	PEB peb = { NULL };
	RTL_USER_PROCESS_PARAMETERS processParams = { NULL };

	auto hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
	if (hProcess == INVALID_HANDLE_VALUE) {
		std::cout << "Invalid process handle\n";
		return false;
	}

	auto heap = GetProcessHeap();
	auto pbiSize = sizeof(PROCESS_BASIC_INFORMATION);

	pbi = static_cast<PPROCESS_BASIC_INFORMATION>(HeapAlloc(heap, HEAP_ZERO_MEMORY, pbiSize));

	if (!pbi)
	{
		CloseHandle(hProcess);
		return false;
	}

	auto pebAddr = GetPebAddress(hProcess);

	SIZE_T bytesRead;
	if (ReadProcessMemory(hProcess, pebAddr, &peb, sizeof(peb), &bytesRead))
	{
		bytesRead = 0;
		if (ReadProcessMemory(hProcess, peb.ProcessParameters, &processParams, sizeof(RTL_USER_PROCESS_PARAMETERS), &bytesRead))
		{
			if (processParams.CommandLine.Length > 0)
			{
				auto buffer = static_cast<WCHAR *>(malloc(processParams.CommandLine.Length * sizeof(WCHAR)));

				if (buffer)
				{
					if (ReadProcessMemory(hProcess, processParams.CommandLine.Buffer, buffer, processParams.CommandLine.Length, &bytesRead))
					{
						const _bstr_t b(buffer);
						if (strstr(static_cast<char const *>(b), "ppapi") || strstr(static_cast<char const *>(b), "plugin-container"))
						{
							CloseHandle(hProcess);
							HeapFree(heap, 0, pbi);
							return true;
						}			
					}
				}
				free(buffer);
			}
		}
	}

	CloseHandle(hProcess);
	HeapFree(heap, 0, pbi);

	return false;
}

std::vector<int> GetProcessId(std::string host, int port)
{
	std::vector<int> processIds;
	DWORD size;

	GetExtendedTcpTable(nullptr, &size, FALSE, AF_INET, TCP_TABLE_OWNER_PID_ALL, 0);

	auto tcp_pid = new MIB_TCPTABLE_OWNER_PID[size];

	if(GetExtendedTcpTable(tcp_pid, &size, FALSE, AF_INET, TCP_TABLE_OWNER_PID_ALL, NULL) != NO_ERROR)
	{
		std::cout << "Failed to get TCP Table\n";
		return processIds;
	}

	for (DWORD i = 0; i < tcp_pid->dwNumEntries; i++)
	{
		auto *owner_pid = &tcp_pid->table[i];
		DWORD ip = owner_pid->dwRemoteAddr;
		std::string ipString = std::to_string(ip & 0xFF) + "." + std::to_string(ip >> 8 & 0xFF) + 
			"." + std::to_string(ip >> 16 & 0xFF) + "." + std::to_string(ip >> 24 & 0xFF);
		if (ntohs(owner_pid->dwRemotePort) == port &&
			ipString == host) {
			auto hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, owner_pid->dwOwningPid);

			if (hProcess)
			{
				TCHAR procName[MAX_PATH];
				if (GetModuleFileNameEx(hProcess, nullptr, procName, MAX_PATH))
				{
					auto hProcessSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
					if (hProcessSnap == INVALID_HANDLE_VALUE)
					{
						std::cout << "Failed\n";
						return processIds;
					}

					PROCESSENTRY32 pe32;
					pe32.dwSize = sizeof(PROCESSENTRY32);

					if (!Process32First(hProcessSnap, &pe32))
					{
						std::cout << "Process32First failed\n";
						CloseHandle(hProcessSnap);
						return processIds;
					}
					std::experimental::filesystem::path p(procName);

					do
					{
						if (pe32.szExeFile == p.filename())
							processIds.push_back(pe32.th32ProcessID);

					} while (Process32Next(hProcessSnap, &pe32));

					CloseHandle(hProcessSnap);
				}
				CloseHandle(hProcess);
			}
		}
		CloseHandle(owner_pid);
	}
	return processIds;
}

void GetRC4Possibilities(int pid)
{
	std::vector<MemoryChunk *> results;
	const auto hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ | PROCESS_VM_OPERATION, false, pid);

	MEMORY_BASIC_INFORMATION mbi;
	SYSTEM_INFO sys_info;

	GetSystemInfo(&sys_info);

	auto addr = reinterpret_cast<uintptr_t>(sys_info.lpMinimumApplicationAddress);
	const auto end = reinterpret_cast<uintptr_t>(sys_info.lpMaximumApplicationAddress);

	while (addr < end)
	{
		const auto offset = 4;

		if (!VirtualQueryEx(hProcess, reinterpret_cast<LPCVOID>(addr), &mbi, sizeof(mbi)))
			break;

		if (mbi.State == MEM_COMMIT && ((mbi.Protect & PAGE_GUARD) == 0) && ((mbi.Protect & PAGE_NOACCESS) == 0)) {
			const auto dump = new unsigned char[mbi.RegionSize + 1];
			memset(dump, 0, mbi.RegionSize + 1);
			if(!ReadProcessMemory(hProcess, mbi.BaseAddress, dump, mbi.RegionSize, nullptr))
			{
				std::cout << "Failed to read memory for " << mbi.BaseAddress;
				break;
			}

			auto maskCount = 0;
			int nToMap[256] = { 0 };
			int removeMap[256] = { 0 };

			for (auto i = 0; i < 256; i++) {
				nToMap[i] = -1;
				removeMap[i] = -1;
			}

			auto matchStart = -1;
			auto matchEnd = -1;

			for (auto i = 0; i < mbi.RegionSize; i+= offset)
			{
				const auto b = (static_cast<int>(dump[i]) + 128) % 256;
				const auto indInMap = (i / 4) % 256;

				const auto deletedNumber = removeMap[indInMap];

				if (deletedNumber != -1)
				{
					nToMap[deletedNumber] = -1;
					maskCount--;
					removeMap[indInMap] = -1;
				}

				if (nToMap[b] == -1)
				{
					maskCount++;
					removeMap[indInMap] = b;
					nToMap[b] = indInMap;
				}
				else
				{
					removeMap[nToMap[b]] = -1;
					removeMap[indInMap] = b;
					nToMap[b] = indInMap;
				}

				if (maskCount == 256)
				{
					if (matchStart == -1)
					{
						matchStart = i - ((256 - 1) * offset);
						matchEnd = i;
					}

					if (matchEnd < i - ((256 - 1) * offset))
					{
						auto mem = new MemoryChunk();
						mem->start = dump + matchStart;
						mem->size = matchEnd - matchStart + 4;
						results.push_back(mem);

						matchStart = i - ((256 - 1) * offset);
					}
					matchEnd = i;
				}
			}
			if (matchStart != -1)
			{
				auto mem = new MemoryChunk();
				mem->start = dump + matchStart;
				mem->size = matchEnd - matchStart + 4;
				results.push_back(mem);
			}
		}
		addr += mbi.RegionSize;
	}

	/* PrintRC4Possibilities */

	const auto offset = 4;
	auto count = 0;
	for (auto mem : results)
	{
		if (mem->size >= 1024 && mem->size <= 1024 + 2 * offset)
		{
			for (auto i = 0; i < (mem->size - ((256 - 1) * offset)); i += offset)
			{
				unsigned char wannabeRC4data[1024] = { 0 };
				unsigned char data[256] = { 0 };
				memcpy(wannabeRC4data, static_cast<unsigned char *>(mem->start) + i, 1024);

				auto isvalid = true;

				for (auto j = 0; j < 1024; j++)
				{
					if (j % 4 != 0 && wannabeRC4data[j] != 0)
					{
						isvalid = false;
						break;
					}
					if (j % 4 == 0)
					{
						data[j / 4] = wannabeRC4data[j];
					}
				}
				if (isvalid)
				{
					for (auto idx = 0; idx < 256; idx++)
						printf("%02X", static_cast<signed char>(data[idx]) & 0xFF);

					std::cout << std::endl;
				}
			}
		}
		delete mem;
	}
	CloseHandle(hProcess);
}


int main(int argc, char **argv)
{
	if (argc == 3) {
		auto pids = GetProcessId(argv[1], strtol(argv[2], nullptr, 10));

		for (auto pid : pids) {
			if (IsFlashProcess(pid)) {
				GetRC4Possibilities(pid);
			}
		}

		if (pids.empty())
			std::cout << "No pids found\n";
	}
    return 0;
}

