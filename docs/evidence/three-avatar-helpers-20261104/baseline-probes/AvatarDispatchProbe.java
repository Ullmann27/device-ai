import at.heinz.deviceai.avatar.*;
import at.heinz.deviceai.cockpit.*;
import android.graphics.Rect;
import android.view.ViewGroup;
import kotlin.Unit;
import java.util.ArrayList;
public class AvatarDispatchProbe {
  static class Renderer implements JarvisAvatarRenderer {
    int received=0;
    public void attach(ViewGroup host,Rect slot){}
    public void onAvatarState(AvatarState state){}
    public void onAnimationEvent(CockpitAnimationEvent event){}
    public void detach(){}
    public void onSlotChanged(Rect slot){}
    public void onPresentation(AvatarPresentation mode){}
    public void onAvatarEvent(AvatarEvent event){received++;}
  }
  public static void main(String[] args) {
    ArrayList<Runnable> queue=new ArrayList<>();
    Renderer old=new Renderer(); Renderer current=new Renderer();
    JarvisAvatarSlot.INSTANCE.register(old);
    AvatarSink sink=JarvisAvatarSlot.INSTANCE.fanOut(task->{queue.add(task);return Unit.INSTANCE;});
    AvatarEvent event=new AvatarEvent.SpeechStarted("probe","synthetic",100L);
    AvatarSinkRegistry.INSTANCE.setSink(ignored->{throw new IllegalStateException("synthetic");});
    boolean escaped=false;
    try { sink.onAvatarEvent(event); } catch (IllegalStateException expected){escaped=true;}
    System.out.println("REGISTRY_FAILURE escaped="+escaped+" renderer_callbacks_queued="+queue.size());
    AvatarSinkRegistry.INSTANCE.setSink(NoOpAvatarSink.INSTANCE);
    sink.onAvatarEvent(event);
    JarvisAvatarSlot.INSTANCE.register(current);
    for(Runnable task:queue) task.run();
    System.out.println("OBSOLETE_RENDERER old_deliveries="+old.received+" current_deliveries="+current.received);
    JarvisAvatarSlot.INSTANCE.register(null);
  }
}
