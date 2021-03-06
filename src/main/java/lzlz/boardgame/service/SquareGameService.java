package lzlz.boardgame.service;

import lombok.extern.slf4j.Slf4j;
import lzlz.boardgame.constant.PlayerState;
import lzlz.boardgame.core.squaregame.PlayerRole;
import lzlz.boardgame.core.squaregame.SquareGame;
import lzlz.boardgame.core.squaregame.entity.Room;
import lzlz.boardgame.core.squaregame.entity.SquareGameData;
import lzlz.boardgame.core.squaregame.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.websocket.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 加入游戏的流程应该是
 * (http)进入房间-->(socket)连接聊天服务器&游戏服务器-->(http)等待双方准备-->(socket)开始
 * createBy lzlz at 2018/2/3 15:26
 * @author : lzlz
 */
@Slf4j
@Service("RoomService")
public class SquareGameService {
    private static final Map<String,Session> chatSessionMap = new HashMap<>();
    private static final Map<String,Session> gameSessionMap = new HashMap<>();

    @Autowired
    HallService hallService;

    public User connect2ChatServer(Room room, String userId, Session chatSession){
        //如果room中有对应的玩家则加入session
        User player = hallService.getUserFromRoomById(room,userId);
        if (player != null){
            putSession(chatSessionMap,userId,chatSession);
        }
        return player;
    }

    public User connect2GameServer(Room room, String userId, Session gameSession){
        User player = hallService.getUserFromRoomById(room,userId);
        if (player != null){
            putSession(gameSessionMap,userId,gameSession);
        }

        return player;
    }

    /**
     * 用户准备 如果所有玩家都准备 开始游戏
     * @return true 开始游戏 false未开始
     */
    public boolean ready(Room room,String userId){
        User user = hallService.getUserFromRoomById(room,userId);
        if (user == null) {
            return false;
        }
        user.setState(PlayerState.Ready);
        User blue = room.getBlue();
        User red = room.getRed();
        if(blue !=null&&red!=null
                &&PlayerState.Ready.equals(blue.getState())&&PlayerState.Ready.equals(red.getState())){
            if(room.getSquareGame()==null){
                startGame(room,blue,red);
                return true;
            }
        }
        return false;
    }
    /*前端 gameData
        role:'Blue',
        size:5,
        blueName:'ABCCCCC',
        blueReady:false,
        blueScore:0,
        redName:'哈哈哈哈哈',
        redReady:false,
        redScore:0,
        state:0,
        boardData:[]
         */
    public SquareGameData fullData(String roomId ,String userId){
        return fullData(hallService.getRoom(roomId),hallService.getUserFromRoomById(roomId,userId));
    }
    public SquareGameData fullData(Room room ,User user){
        SquareGameData data;
        if (room.getSquareGame() != null) {
            data = room.getSquareGame().getSquareGameData();
        }else{
            data = new SquareGameData();

        }
        data.setSize(room.getSize().getValue());
        User blue = room.getBlue();
        if(blue!=null){
            if(blue.equals(user)){
                data.setRole(PlayerRole.Blue);
            }
            data.setBlueName(blue.getName());
            data.setBlueReady(PlayerState.Ready.equals(blue.getState()));
        }
        User red = room.getRed();
        if(red!=null){
            if(red.equals(user)){
                data.setRole(PlayerRole.Red);
            }
            data.setRedName(red.getName());
            data.setRedReady(PlayerState.Ready.equals(red.getState()));
        }
        return data;
    }
    private void startGame(Room room,User blue,User red){
        SquareGame game = new SquareGame(room.getSize(),blue,red);
        room.setSquareGame(game);
    }

    private void putSession(Map<String,Session> sessionMap,String userId,Session newSession){
        Session oldSession = sessionMap.get(userId);
        if(oldSession!=null&&oldSession.isOpen()){
            //关闭旧的session
            try {
                oldSession.close();
            } catch (IOException ignored) {
            }
        }
        sessionMap.put(userId,newSession);
    }

    /**
     * 遍历 roomid对应房间聊天服务器的所有session
     * @param roomId 房间UUID
     * @param consumer 对session的操作
     */
    public void forEachChatSession(String roomId, Consumer<Session> consumer){
        Room room = hallService.getRoom(roomId);
        forEachSession(chatSessionMap,room,consumer);
    }

    /**
     * @param room 房间
     * @param consumer 对session的操作
     */
    public void forEachGameSession(Room room, Consumer<Session> consumer){
        forEachSession(gameSessionMap,room,consumer);
    }

    private void forEachSession(Map<String,Session> sessionMap,Room room, Consumer<Session> consumer){
        if (room == null) {
            return;
        }
        User blue = room.getBlue();
        User red = room.getRed();
        synchronized (this) {
            Session session;
            if (blue != null) {
                session = sessionMap.get(blue.getId());
                if (session != null) {
                    consumer.accept(session);
                }
            }
            if (red != null) {
                session = sessionMap.get(red.getId());
                if (session != null) {
                    consumer.accept(session);
                }
            }
        }
    }
    /**
     * 认输
     */
    public void giveup(String roomId, String userId) {
        Room room = hallService.getRoom(roomId);
        if (room == null) {
            log.info("null room");
            return;
        }
        User user = hallService.getUserFromRoomById(room,userId);
        if (user == null) {
            log.info("null user");
            return;
        }
        SquareGame game = room.getSquareGame();
        if (game != null) {
            game.giveUp(user);
            log.debug("give up");
        }
    }

    /**
     * 离开房间
     */
    public void leaveRoom(String roomId, String userId){
        Room room = hallService.getRoom(roomId);
        if (room ==null) {//如果存在房间，才移除房间中的用户
            return;
        }
        User player = null;
        User blue = room.getBlue();
        if(blue!=null&&userId.equals(blue.getId())){
            room.setBlue(null);
            player = blue;
            player.setRoomId(null);
        }
        User red = room.getRed();
        if(red!=null&&userId.equals(red.getId())){
            room.setRed(null);
            player = red;
            player.setRoomId(null);
        }
        if (room.getRed()==null&&room.getBlue()==null){//如果全部都是空 删除此房间
            hallService.removeRoom(roomId);
        }
        SquareGame game = room.getSquareGame();
        if (game != null) {//game!=null说明 游戏已经开始，此玩家认输
            game.giveUp(player);
        }
        synchronized (this){
            chatSessionMap.remove(userId);
            gameSessionMap.remove(userId);
//            Session session = chatSessionMap.remove(userId);
//            if (session != null&&session.isOpen()) {
//                try {
//                    session.close();
//                } catch (Exception ignored) {
//                }
//            }
//            session = gameSessionMap.remove(userId);
//            if (session != null&&session.isOpen()) {
//                try {
//                    session.close();
//                } catch (Exception ignored) {
//                }
//            }
        }
    }

    public void removeChatSession(String userId){
        Session session;
        synchronized (chatSessionMap){
            session = chatSessionMap.remove(userId);
            if(session!=null&&session.isOpen())
                try {
                    session.close();
                } catch (IOException ignore) {
                }
        }
    }

    public void removeGameSession(String userId){
        Session session;
        synchronized (gameSessionMap){
            session =  gameSessionMap.remove(userId);
        }
        if(session!=null&&session.isOpen())
            try {
                session.close();
            } catch (IOException ignore) {
            }
    }


}
