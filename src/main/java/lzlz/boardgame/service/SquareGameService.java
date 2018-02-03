package lzlz.boardgame.service;

import lzlz.boardgame.constant.PlayerState;
import lzlz.boardgame.core.squaregame.PlayerRole;
import lzlz.boardgame.core.squaregame.SquareGame;
import lzlz.boardgame.core.squaregame.entity.Room;
import lzlz.boardgame.core.squaregame.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.websocket.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 加入游戏的流程应该是
 * (http)进入房间-->(socket)连接聊天服务器&游戏服务器-->(http)等待双方准备-->(socket)开始
 * createBy lzlz at 2018/2/3 15:26
 * @author : lzlz
 */
@Service("RoomService")
public class SquareGameService {
    private static final Map<String,Session> chatSessionMap = new HashMap<>();
    private static final Map<String,Session> gameSessionMap = new HashMap<>();

    @Autowired
    HallService hallService;

    public User connect2ChatServer(Room room, String userId, Session chatSession){
        //如果room中有对应的玩家则加入session
        User player = room.getUserList().stream()
                .filter(player1 -> player1.getId().equals(userId))
                .findFirst().orElse(null);
        if (player != null){
            putSession(chatSessionMap,userId,chatSession);
        }
        return player;
    }

    public User connect2GameServer(Room room, String userId, Session gameSession){
        User player = null;
        //squareGame的room只有两个玩家
        List<User> userList = room.getUserList();
        if(userList.size()>0&&userList.get(0).getId().equals(userId)){
            player = userList.get(0);
            player.setPlayerRole(PlayerRole.Blue);
        }else if(userList.size()>1&&userList.get(1).getId().equals(userId)){
            player = userList.get(1);
            player.setPlayerRole(PlayerRole.Red);
        }
        if (player != null){
            putSession(gameSessionMap,userId,gameSession);
        }
        return player;
    }

    /**
     * 用户准备 如果所有玩家都准备 开始游戏
     */
    public void ready(Room room,String userId){
        room.getUserList().stream()
                .filter(player1 -> player1.getId().equals(userId))
                .findFirst().ifPresent(player -> player.setState(PlayerState.Ready));
        boolean isAllReady = true;
        for (User player :room.getUserList()) {
            isAllReady = isAllReady && PlayerState.Ready.equals(player.getState());
        }
        if(isAllReady&&room.getUserList().size()==2){
            startGame(room,room.getUserList().get(0),room.getUserList().get(1));
        }
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
     * 从一个房间中移除用户，若用户为0则移除房间
     */
    public void removePlayer(Room room, String userId){
        List<User> userList = room.getUserList();
        int size = userList.size();
        for (int i = 0; i <size;i++) {
            User user = userList.get(i);
            if(userId.equals(user.getId())){
                SquareGame game = room.getSquareGame();
                if (game != null) {
                    game.giveUp(user);
                }
                userList.remove(user);
            }
        }
        if(userList.size()==0)
            hallService.removeRoom(room.getId());
    }

    /**
     * 遍历 roomid对应房间聊天服务器的所有session
     * @param roomId 房间UUID
     * @param consumer 对session的操作
     */
    public void forEachChatSession(String roomId, Consumer<Session> consumer){
        Room room = hallService.getRoom(roomId);
        synchronized (chatSessionMap) {
            room.getUserList().forEach(user -> consumer.accept(chatSessionMap.get(user.getId())));
        }
    }

    /**
     * 离开房间
     */
    public void leaveRoom(String roomId,String userId){
        Room room = hallService.getRoom(roomId);
        if (room ==null) {//如果存在房间，才移除房间中的用户
            return;
        }
        User user = room.getUserList().stream().filter(u->u.getId().equals(userId)).findFirst().orElse(null);
        room.getUserList().remove(user);

        try {
            synchronized (this){
                Session session = chatSessionMap.remove(userId);
                if (session != null) {
                    session.close();
                }
                session = gameSessionMap.remove(userId);
                if (session != null) {
                    session.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeChatSession(String userId){
        synchronized (chatSessionMap){
            chatSessionMap.remove(userId);
        }
    }

    public void removeGameSession(String userId){
        synchronized (gameSessionMap){
            gameSessionMap.remove(userId);
        }
    }

}