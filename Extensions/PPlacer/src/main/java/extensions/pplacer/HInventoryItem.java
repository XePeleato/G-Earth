package extensions.pplacer;

import gearth.protocol.HPacket;

import java.util.LinkedList;
import java.util.List;

public class HInventoryItem {
    boolean mIsFloorFurni;
    int mId;
    int mTypeId;
    int mCategory;
    List<Object> stuff;
    boolean mGroupable;
    boolean mTradeable;
    boolean mMPAllowed;
    int mSecsToExpiry;
    boolean mRentPeriodStarted;
    int mRoomId;

    public void readStuff(HPacket packet) {
        stuff = new LinkedList<>();

        int cat2 = mCategory & 0xFF;

        //System.out.println("case: " + cat2);

        switch (cat2) {
            case 0: {
                stuff.add(packet.readString());
                break;
            }
            case 1: {
                int max = packet.readInteger();

                for (int i = 0; i < max; i++) {
                    stuff.add(packet.readString());
                    stuff.add(packet.readString());
                }
                break;
            }
            case 2: {
                int max = packet.readInteger();
                for (int i = 0; i < max; i++) {
                    stuff.add(packet.readString());
                }
                break;
            }
            case 3: {
                stuff.add(packet.readString());
                stuff.add(packet.readInteger());
                break;
            }
            case 5: {
                int max = packet.readInteger();
                for (int i = 0; i < max; i++)
                    stuff.add(packet.readInteger());
                break;
            }
            case 6: {
                packet.readString();
                packet.readInteger();
                packet.readInteger();
                int max = packet.readInteger();

                for (int i = 0; i < max; i++) {
                    packet.readInteger();
                    int dataCount = packet.readInteger();
                    for (int j = 0; j < dataCount; j++)
                        packet.readString();
                }
                break;
            }
        case 7: {
            packet.readString();
            packet.readInteger();
            packet.readInteger();
            break;
        }
        }
        if ((mCategory & 0xFF00 & 0x100) > 0) {
            stuff.add(packet.readInteger());
            stuff.add(packet.readInteger());
        }
    }

    public static List<HInventoryItem> parse(HPacket packet) {
        List<HInventoryItem> ret = new LinkedList<>();
        packet.readInteger();
        packet.readInteger();
        int len = packet.readInteger();
        //System.out.println("len: " + len);
        for (int i = 0; i < len; i++) {
            //System.out.println("i: " + i + "/" + len);
            HInventoryItem obj = new HInventoryItem();
            packet.readInteger();

            String test = packet.readString();

            obj.mIsFloorFurni = test.equals("S");
            obj.mId = packet.readInteger();
            obj.mTypeId = packet.readInteger();
            packet.readInteger();
            obj.mCategory = packet.readInteger();
            obj.readStuff(packet);

            obj.mGroupable = packet.readBoolean();
            packet.replaceBoolean(packet.getReadIndex(), true);
            obj.mTradeable = packet.readBoolean();
            packet.replaceBoolean(packet.getReadIndex(), true);
            packet.readBoolean();

            packet.replaceBoolean(packet.getReadIndex(), true);
            obj.mMPAllowed = packet.readBoolean();
            obj.mSecsToExpiry = packet.readInteger();
            obj.mRentPeriodStarted = packet.readBoolean();
            obj.mRoomId = packet.readInteger();

            if (obj.mIsFloorFurni) {
                packet.readString();
                packet.readInteger();
            }

            ret.add(obj);
        }
        return ret;
    }
}
