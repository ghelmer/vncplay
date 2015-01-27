import java.awt.event.*;

public interface VncEventListener {
    public void screenEvent(int x, int y, int w, int h);
    public void mouseEvent(MouseEvent e);
    public void keyEvent(KeyEvent e);
}
