package actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.fasterxml.jackson.databind.JsonNode;
import models.BasicBot;
import models.Builders.JSONBuilder;
import models.GameSession;
import models.User;
import models.Utility.Point;
import play.Logger;
import play.libs.Json;


/**
 * Is reponsible for conducting game flow.
 * Interprets messages sent from {@link BrowserEntryPointActor} or
 * {@link VirtualBrowserActor} and propagates messages to every user in room.
 */
public class GameSessionActor extends AbstractActor {
    private final GameSession gameSession;

    public static Props props(GameSession gameSession) {
        return Props.create(GameSessionActor.class, gameSession);
    }

    public GameSessionActor(GameSession gameSession) {
        this.gameSession = gameSession;
        Logger.info("{} started", this.getClass());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(String.class, message -> {
                        ActorRef browser = sender();

                        // Otrzymana wiadomosc (String) parsuje na obiekt JsonNode
                        JsonNode jn = Json.parse(message);
                        // Mapowanie JsonNode na obiekt klasy JsonMsg
                        String type = jn.findPath("type").textValue();

                        if(type.equals("move")){
                            Point pointA = new Point(jn.findPath("x1").asInt(), jn.findPath("y1").asInt());
                            Point pointB = new Point(jn.findPath("x2").asInt(), jn.findPath("y2").asInt());

                            String username = jn.findPath("username").asText();
                            User tempUser = gameSession.getRoom().getUsers().get(username);

                            if(this.gameSession.getGameBoard().makeAMove(pointA, pointB, tempUser)){
                                gameSession.getRoom().update(gameSession.getGameBoard().buildMap(new JSONBuilder()), self(), tempUser);
                            }
                            else{
                                tempUser.tell("{ \"type\" : \"move\", \"cond\" : false }", self());
                            }
                        }

                        if(type.equals("repaint")){
                            String username = jn.findPath("username").asText();
                            User tempUser = gameSession.getRoom().getUsers().get(username);
                            tempUser.tell(this.gameSession.getGameBoard().buildMap(new JSONBuilder()), self());
                            //browser.tell(this.gameSession.getGameBoard().buildMap(new JSONBuilder()), self());
                        }

                        if(type.equals("WebSocketInit")) {
                            String username = jn.findPath("username").asText();
                            User tempUser = gameSession.getRoom().getUsers().get(username);
                            tempUser.setActorRef(browser);
                            gameSession.addToQueue(tempUser);
                            tempUser.tell(this.gameSession.getGameBoard().buildMap(new JSONBuilder()), self());
                            Logger.info("Pairing {} with WebSocket {}", username, browser);
                        }

                        if(type.equals("pass")){
                            this.gameSession.passToNext();
                            // tell the room
                            gameSession.getRoom().tell("{ \"type\" : \"refresh\" }", self());
                        }

                        if(type.equals("status")){
                            String username = jn.findPath("username").asText();
                            User tempUser = gameSession.getRoom().getUsers().get(username);

                            browser.tell("{ \"type\" : \"status\", \"canMove\" : "
                                    + tempUser.getActivity()
                                    + "}", self());
                        }

                        // Send back to room WHOLE Game Session
                        //gameSession.getRoom().tell(gameSession.getGameBoard().buildMap(new JSONBuilder()), self());


                    // TODO: Process Client move request


                })
                .build();
    }
}
