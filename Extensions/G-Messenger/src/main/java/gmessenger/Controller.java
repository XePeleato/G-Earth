package gmessenger;

import com.sun.webkit.dom.KeyboardEventImpl;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.w3c.dom.Element;
import org.w3c.dom.events.EventTarget;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    public BorderPane borderPane;
    WebView webView;
    boolean initialized;
    private GMessenger gMessenger;

    public long activeContact() {
        return gMessenger.getActiveContact();
    }

    public void setOwnInfo(String name, String figureId) {
        Platform.runLater(() -> {
            webView.getEngine().executeScript("document.getElementById('habboname').textContent = '" + name + "';");
            webView.getEngine().executeScript("document.getElementById('profile-img').src = '" + getFigureUrl(figureId, false) + "';");
        });
    }

    public void sendChat(long activeContact) {
        String text = (String) webView.getEngine().executeScript("document.getElementById('textInput').value;");
        webView.getEngine().executeScript("document.getElementById('textInput').value = '';");
        gMessenger.addMessage(activeContact(), text, false);

        updatePreview(new ChatMessage(activeContact, text, false));
        gMessenger.sendToServer(new HPacket(33 /*SendMessage*/, activeContact, text));
    }

    public void updatePreview(ChatMessage chatMessage) {
        Platform.runLater(() -> webView.getEngine().executeScript("document.getElementById('preview" + chatMessage.getSenderId() + "').innerHTML = '" + chatMessage.getContent() + "';"));
    }

    public void toggleContact(long oldId, long id, HProfile contact) {
        if (oldId != -1) {
            webView.getEngine().executeScript("document.getElementById('c" + oldId + "').className = \"contact\"");
        }
        webView.getEngine().executeScript("document.getElementById('c" + id + "').className = \"contact active\"");

        webView.getEngine().executeScript("document.getElementById('currentChatName').textContent = '" + contact.getName() + "';");
        webView.getEngine().executeScript("document.getElementById('currentContactPicture').src = '" + getFigureUrl(contact.getFigureId(), false) + "';");


        //refreshMessageView();
    }

    public void refreshMessageView(long activeContact, List<ChatMessage> messageList, HProfile friend, String ownFigureId) {
        if (activeContact == -1)
            return;

        StringBuilder sb = new StringBuilder();

        Platform.runLater(() -> webView.getEngine().executeScript("document.getElementById('messageList').innerHTML = '';"));

        for(ChatMessage chatMessage : messageList) {
            String avatarUrl = chatMessage.isIncoming() ? getFigureUrl(friend.getFigureId(), false) :
                    getFigureUrl(ownFigureId, true);

            sb.append("<li class=\"").append(chatMessage.isIncoming() ? "replies" : "sent").append("\">")
                    .append("<img src=\"").append(avatarUrl).append("\" alt=\"\" />")
                    .append("<p>").append(chatMessage.getContent()).append("</p>")
                    .append("</li>");
        }

        Platform.runLater(() -> webView.getEngine().executeScript("document.getElementById('messageList').innerHTML = '" + escapeMessage(sb.toString()) + "';"));

    }

    public void addCard(long id, String name, String figureId, String message) {
        StringBuilder contact = new StringBuilder("<li id=\"c" + id + "\" class=\"contact\"").append(" onclick=\"app.toggleContact(").append(id).append(")\">")
                .append("<div class=\"wrap\">")
                .append("<span class=\"contact-status online\"></span>")
                .append("<img src=\"").append(getFigureUrl(figureId, false)).append("\" alt=\"\" />")
                .append("<div class=\"meta\">")
                .append("<p class=\"name\">").append(name).append("</p>")
                .append("<p id=\"preview").append(id).append("\" class=\"preview\">").append(message).append("</p>")
                .append("</div>")
                .append("</div>")
                .append("</li>");

        Platform.runLater(() -> webView.getEngine().executeScript("document.getElementById('contactList').innerHTML += '" + escapeMessage(contact.toString()) + "';"));
    }

    private static String getFigureUrl(String figureId, boolean facingLeft) {
        return "https://www.habbo.com/habbo-imaging/avatarimage?size=m&figure=" + figureId + "&direction=2&head_direction=" + (facingLeft ? 2 : 4) + "&headonly=1";
    }

    private String escapeMessage(String text) {
        return text
                .replace("\n\r", "<br />")
                .replace("\n", "<br />")
                .replace("\r", "<br />")
                .replace("'", "\\'");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        webView = new WebView();
        borderPane.setCenter(webView);

        webView.getEngine().getLoadWorker().stateProperty().addListener((observableValue, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                initialized = true;

                JSObject window = (JSObject) webView.getEngine().executeScript("window");
                window.setMember("app", gMessenger);

                Element input = webView.getEngine().getDocument().getElementById("textInput");
                ((EventTarget) input).addEventListener("keydown", event -> {
                    if ("Enter".contentEquals(((KeyboardEventImpl) event).getKeyIdentifier()))
                        gMessenger.sendChat();
                }, true);

            }
        });

        webView.getEngine().load(GMessenger.class.getResource("gmessenger.html").toString());
    }

    public GMessenger getgMessenger() {
        return gMessenger;
    }

    public void setgMessenger(GMessenger gMessenger) {
        this.gMessenger = gMessenger;
    }
}
