package gmessenger;

import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.*;

@ExtensionInfo(
        Title = "G-Messenger",
        Description = "Adds private chat functionality",
        Version = "1.0",
        Author = "XePeleato"
)

public class GMessenger extends ExtensionForm {
    ObservableMap<Long, List<ChatMessage>> messages;
    Map<Long, HProfile> friendCache;
    List<Long> openChats;


    public long getActiveContact() {
        return activeContact;
    }

    private long activeContact = -1;
    private String ownFigureId;
    private String ownName;
    private Controller controller;

    private void onMessageArrived(MapChangeListener.Change<? extends Long, ? extends List<ChatMessage>> change) {
        if (change.wasRemoved() || !change.getValueAdded().get(change.getValueAdded().size() - 1).isIncoming())
            return;

        List<ChatMessage> chat = change.getValueAdded();


        if (chat.size() == 1 && !openChats.contains(change.getKey())) {
            // first message, gotta look for the name/details of this friend
            HProfile friend = friendCache.get(change.getKey());
            openChats.add(change.getKey());
            controller.addCard(change.getKey(), friend.getName(), friend.getFigureId(), chat.get(0).getContent());
        }
        else
            controller.updatePreview(chat.get(chat.size() - 1));

    }

    private void friendListFragmentParser(HMessage message) {
        HProfile[] friends = FriendListFragment.parse(message.getPacket());
        for (HProfile friend : friends)
            friendCache.put(friend.getId(), friend);
    }

    private void onOwnProfile(HMessage message) {
        HPacket packet = message.getPacket();

        packet.readLong();
        ownName = packet.readString();
        ownFigureId = packet.readString();

        controller.setOwnInfo(ownName, ownFigureId);
    }



    public void addMessage(long senderId, String content, boolean incoming) {
        List<ChatMessage> value = messages.get(senderId);

        if (value == null)
            value = new LinkedList<>();

        value.add(new ChatMessage(senderId, content, incoming));

        messages.put(senderId, value);

        refreshMessageView();
    }


    public void toggleContact(Object asd) {
        int id = (int) asd;

        HProfile contact = friendCache.get((long) id);

        controller.toggleContact(activeContact, id, contact);

        activeContact = id;

        refreshMessageView();
    }

    public void refreshMessageView() {
        List<ChatMessage> messageList = messages.get(activeContact);
        HProfile friend = friendCache.get(activeContact);

        controller.refreshMessageView(activeContact, messageList, friend, ownFigureId);
    }

    public void sendChat() {
        if (activeContact == -1)
            return;

        controller.sendChat(activeContact);
    }


    @Override
    protected void initExtension() {
        super.initExtension();

        messages = FXCollections.observableHashMap();
        friendCache = new HashMap<>();
        openChats = new LinkedList<>();

        messages.addListener(this::onMessageArrived);

        intercept(HMessage.Direction.TOCLIENT, 134 /*MessengerNewConsoleMessage*/, (message) -> {
            if (!primaryStage.isFocused())
                Platform.runLater(() -> primaryStage.toFront());

            HPacket packet = message.getPacket();
            addMessage((int)packet.readLong(), packet.readString(), true);
        });

        intercept(HMessage.Direction.TOCLIENT, 915/*ExtendedProfile*/, message -> {
            HPacket packet = message.getPacket();
            long id = packet.readLong();

            if (!friendCache.containsKey(id) || openChats.contains(id))
                return;

            controller.addCard(id, packet.readString(), packet.readString(), "");
            openChats.add(id);
        });

        intercept(HMessage.Direction.TOCLIENT, 14 /*FriendListFragment*/, this::friendListFragmentParser);

        intercept(HMessage.Direction.TOCLIENT, 5 /*UserObject*/, this::onOwnProfile);
    }

    @Override
    protected void onShow() {
        super.onShow();
        primaryStage.setAlwaysOnTop(true);
    }


    @Override
    public ExtensionForm launchForm(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(GMessenger.class.getResource("G-Messenger.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("G-Messenger");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(true);

        controller = loader.getController();
        controller.setgMessenger(this);

        return this;
    }

    public static void main(String[] args) {
        runExtensionForm(args, GMessenger.class);
    }
}
