package lzlz.boardgame.socket;

import lombok.extern.slf4j.Slf4j;
import lzlz.boardgame.util.StringUtils;

import javax.websocket.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public abstract class AbstractChatEndPoint implements ChatEndPoint {
    protected static SimpleDateFormat formater = new SimpleDateFormat("HH:mm:ss");
    protected String name;

    @Override @OnMessage
    public void onMessage(String message, Session session) {
        broadcast(message,session);
    }

    @Override @OnError
    public void onError(Session session, Throwable error) {
        log.warn("session:"+session.getId()+"发生错误");
        error.printStackTrace();
        try {
            session.close();
        } catch (IOException ignored) {
        }
    }

    @Override @OnClose
    public void onClose(Session session) {
        removeSession(session);
    }

    protected void sendText(Session session, String head, String message) throws IOException{
        if(message!=null && !"".equals(message.trim()))
            session.getBasicRemote().sendText("<pre>"+head + message+"</pre>");
    }

    /**
     * 删除消息的特殊符号
     */
    protected String getText(String text){
        //删除普通标签
        text = StringUtils.filterHTML(text);
        //删除转义字符
        text = text.replaceAll("&.{2,6}?;", "");
        return text;
    }

    protected String getSystemPrefix(){
        return "<span class=\"my-bg-green chat-head\">"
                +formater.format(new Date())
                +"&nbsp;[系统信息]:</span>&nbsp;";
    }
    protected String getUserPrefix(String name){
        return "<span class=\"layui-bg-gray chat-head\">"
                +formater.format(new Date())
                +"&nbsp;"+name+":</span>&nbsp;";
    }
    protected String getSpecialUserInfoPrefix(String name,String role){
        return "<span class=\"layui-bg-gray chat-head\">"
                +formater.format(new Date())
                +"&nbsp;["+role+"]"+name+":</span>&nbsp;";
    }
}
