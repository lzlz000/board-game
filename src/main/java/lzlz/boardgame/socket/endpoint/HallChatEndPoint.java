package lzlz.boardgame.socket.endpoint;

import lzlz.boardgame.constant.UserRole;
import lzlz.boardgame.entity.User;
import lzlz.boardgame.socket.AbstractChatEndPoint;
import lzlz.boardgame.socket.SessionWrapper;
import org.springframework.stereotype.Component;

import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint(value = "/socket/hallchat/{name}")
@Component
public class HallChatEndPoint extends AbstractChatEndPoint {
    private static CopyOnWriteArraySet<SessionWrapper> sessionSet = new CopyOnWriteArraySet<>();

    @Override @OnOpen
    public void onOpen(Session session,@PathParam("name") String name) {
        try {
            super.name=name;
            addSession(session,name);
            session.getBasicRemote().sendText(getSystemPrefix()+"与服务器连接成功");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void broadcast(String message, Session thisSession){
        String text = getText(message);
        for (SessionWrapper wrapper : sessionSet) {
            try {
                Session session = wrapper.getSession();
                if(session.equals(thisSession)){
                    wrapper.setLastActiveTime(new Date());
                    sendText(session,getSpecialUserInfoPrefix(this.name,"我"),text);
                }else if(session.isOpen()){
                    sendText(session,getUserPrefix(this.name),text);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void addSession(Session session, String name) {
        SessionWrapper wrapper = new SessionWrapper(session,new User(0, UserRole.Normal,""));
        sessionSet.add(wrapper);
    }

    @Override
    public void removeSession(Session session) {
        sessionSet.remove(new SessionWrapper(session,null));//SessionWrapper的equals方法重写为比较session
    }

}
