package clojure.kilim;

import kilim.Mailbox;
import kilim.Pausable;

import java.util.ArrayList;

/**
 * User: antonio
 * Date: 29/09/2011
 * Time: 19:53
 */
public class SelectiveMailbox<T> extends Mailbox<T> {

    protected ArrayList<T> buffer;
    protected int bufferIndex;

    public SelectiveMailbox() {
        super();
        buffer = new ArrayList<T>();
        bufferIndex = 0;
    }

    public T get() throws Pausable {
        if(buffer.size()==0 || bufferIndex==buffer.size()-1) {
            return super.get();
        } else {
            return buffer.get(bufferIndex);
        }
    }

    public void accept() {
        if(buffer.size()>0) {
            for(int i=bufferIndex; i<buffer.size()-1; i++) {
                buffer.set(i,buffer.get(i+1));
            }
            buffer.remove(buffer.size()-1);
        }
        bufferIndex=0;
    }

    public void reject(T object) {
        if(buffer.size()==0) {
            buffer.add(object);
        } else if(bufferIndex==buffer.size()-1) {
            buffer.add(object);
            bufferIndex++;
        } else {
            bufferIndex++;
        }
    }
}
