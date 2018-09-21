package main.extensions.MarketplaceAPI;


import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import main.extensions.Extension;
import main.protocol.HMessage;
import main.protocol.HPacket;
import org.bson.BsonArray;
import org.bson.BsonInt32;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

public class MarketplaceParser extends Extension {

    private MongoDatabase mongoDatabase;
    public static void main(String[] args)
    {
        new MarketplaceParser(args);

    }

    /**
     * Makes the connection with G-Earth, pass the arguments given in the Main method "super(args)"
     *
     * @param args arguments
     */
    private MarketplaceParser(String[] args) {
        super(args);
    }

    @Override protected void init()
    {
        MongoClient mongoClient = MongoClients.create("whatever");

        mongoDatabase = mongoClient.getDatabase("marketplace");

        intercept(HMessage.Side.TOCLIENT, 3456, this::ParseMarketplacePacket);
        intercept(HMessage.Side.TOCLIENT, 3006, this::ParseGraphicsData);
    }


    private void setStuffData(HPacket packet)
    {
        int kind = packet.readInteger();
        switch(kind)
        {
            case 0: // RegularFurni
                packet.readString();
                break;
            case 1: // MapStuffData aka plants
            {
                int max = packet.readInteger();
                for (int i = 0; i < max; i++) {
                    packet.readString();
                    packet.readString();
                }
            }
                break;
            case 2: // StringArrayStuffData
            {
                int max = packet.readInteger();
                for (int i = 0; i < max; i++)
                    packet.readString();
            }
                break;
            case 3: // idk about this one lol
                packet.readString();
                packet.readInteger();
                break;
            case 4: // neither about this one
                break;
            case 5: // IntArrayStuffData
            {
                int max = packet.readInteger();

                for (int i = 0; i < max; i++)
                    packet.readInteger();
            }
                break;
            case 6: // HighScoreStuffData
            {
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
            }
                break;
            case 7: // Crackables (Eggs and stuff)
                packet.readString();
                packet.readInteger();
                packet.readInteger();
                break;
        }
    }

    private void ParseMarketplacePacket(HMessage message)
    {
        HPacket packet = message.getPacket();
        int totalObjects = packet.readInteger();

        for (int i = 0; i < totalObjects; i++) {
            int offerId = packet.readInteger();
            int config1 = packet.readInteger();
            int config2 = packet.readInteger();
            int furniId = 0;
            int ltdNumber = 0, ltdMaxNumber = 0;

            if (config2 == 1) {
                furniId = packet.readInteger();
                setStuffData(packet);
            }
            else if (config2 == 2)
            {
                furniId = packet.readInteger();
                packet.readString(); // extraData
            }
            else if (config2 == 3)
            {
                furniId = packet.readInteger();
                ltdNumber = packet.readInteger();
                ltdMaxNumber = packet.readInteger();
            }

            int price = packet.readInteger();
            int status = packet.readInteger();
            int averagePrice = packet.readInteger();
            int quantity = packet.readInteger();

                LogFurni(furniId, price, quantity);
            MongoCollection<Document> collection = mongoDatabase.getCollection("furni");
            Document doc = new Document("spriteId", furniId)
                    .append("ltdNumber", ltdNumber)
                    .append("price", price)
                    .append("avgPrice", averagePrice)
                    .append("quantity", quantity)
                    .append("lastPrices", new BsonArray())
                    .append("lastTrades", new BsonArray());

            collection.replaceOne(new Document("spriteId", furniId), doc, new UpdateOptions().upsert(true), new SingleResultCallback<UpdateResult>() {
                @Override
                public void onResult(UpdateResult updateResult, Throwable throwable) {
                    System.out.println("Inserted");
                }
            });
            sendToServer(new HPacket(1311).appendInt(1).appendInt(furniId));
        }

        packet.readInteger(); // amount of objects, the same as above of the loop
    }

    private void ParseGraphicsData(HMessage message)
    {
        HPacket packet = message.getPacket();
        int actualPrice = packet.readInteger();
        int quantity = packet.readInteger();
        int furniId = packet.readInteger(packet.getBytesLength() - 4);
        packet.readInteger();
        packet.readInteger();

        System.out.println("Grahpics for: " + furniId);

        BsonArray lastPricesBson = new BsonArray();
        BsonArray lastTradesBson = new BsonArray();

        int day = packet.readInteger() + 30;
        while (day != furniId && day < 31)
        {
            System.out.println("Day: " + day);
            int lastPrice = packet.readInteger();
            if (lastPrice > 0)
                lastPricesBson.add(new BsonInt32(lastPrice));

            int lastTrade = packet.readInteger();

            if (lastTrade > 0)
                lastTradesBson.add(new BsonInt32(lastTrade));

            day = packet.readInteger() + 30;
        }


        MongoCollection<Document> collection = mongoDatabase.getCollection("furni");
        collection.updateOne(eq("spriteId", furniId), new Document("$set", new Document("lastPrices", lastPricesBson).append("lastTrades", lastTradesBson)), new SingleResultCallback<UpdateResult>() {
            @Override
            public void onResult(UpdateResult updateResult, Throwable throwable) {
                if (updateResult == null)
                    System.out.println(throwable.toString());
                else
                    System.out.println(updateResult.getMatchedCount());
            }
        });
    }

    private void LogFurni(int spriteId, int price, int qty)
    {
        System.out.println("Spriteid: " + spriteId + " | Price: " + price + " | Qty: " + qty);
    }

    @Override
    protected String getTitle() {
        return "Marketplace API";
    }

    @Override
    protected String getDescription() {
        return "Get marketplace infos";
    }

    @Override
    protected String getVersion() {
        return "1.0";
    }

    @Override
    protected String getAuthor() {
        return "XePeleato";
    }
}
