package clojure;

import kilim.analysis.Detector;
import kilim.tools.Weaver;

/**
  * User: antonio
 * Date: 23/09/2011
 * Time: 13:41
  */
public class weaver {
    public static void main (String[] args) {
        System.out.println("CLASS PATH "+System.getProperty("java.class.path"));
        Weaver.outputDir = "target/classes";

        if(args[0].compareTo("ITaskFn")==0) {
            System.out.println("*** clojure.lang.ITaskFn");
            Weaver.weaveClass("clojure.lang.ITaskFn", Detector.DEFAULT);
        } else if(args[0].compareTo("ATaskFn")==0) {
            System.out.println("*** clojure.lang.ATaskFn");
            Weaver.weaveClass("clojure.lang.ATaskFn", Detector.DEFAULT);
        } else if(args[0].compareTo("ATaskFunction")==0) {
            System.out.println("*** clojure.lang.ATaskFunction");
            Weaver.weaveClass("clojure.lang.ATaskFunction", Detector.DEFAULT);
        } else if(args[0].compareTo("ATaskFunction$1")==0) {
            System.out.println("*** clojure.lang.ATaskFunction$1.class");
            Weaver.weaveClass("clojure.lang.ATaskFunction$1", Detector.DEFAULT);
        } else {
            System.err.println("Error, I don't know how to weave "+args[0]);
        }
    }
}
