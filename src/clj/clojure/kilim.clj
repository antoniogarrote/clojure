(in-ns 'clojure.core)

(import [clojure.asm ClassWriter Type Opcodes ClassVisitor]
        [java.lang.reflect Modifier Constructor]
        [clojure.asm.commons Method GeneratorAdapter]
        [clojure.lang DynamicClassLoader]
        [kilim Mailbox Pausable Task])

;; generators

(defn kilim-generator
  ([generator-fn] (.newInstance (class generator-fn))))

(defn kilim-generator-seq
  ([f]
     (letfn [(next-value [g] (let [next (.next g)] (if (nil? next) next (lazy-cat (list next) (next-value g)))))]
       (let [g (kilim-generator f)
             first-value (let [v (.next g)] (if (nil? v) v (list v)))]
         (lazy-cat
          first-value
          (next-value g))))))

;; proxies

(defn- to-types ([cs] (if (pos? (count cs))
                       (into-array (map (fn [^Class c] (. Type (getType c))) cs))
                       (make-array Type 0))))

(defn kilim-http-handler
  ([^clojure.lang.ITaskFn fn]
     (let [cv (new ClassWriter (. ClassWriter COMPUTE_MAXS))
           cname (str "clojure/lang/" (gensym "KilimHTTPProxy__"))
           ctype (. Type (getObjectType cname))
           super kilim.http.HttpSession
           super-type (. Type (getType kilim.http.HttpSession))
           itaskfn-type (. Type (getType clojure.lang.ITaskFn))
           ataskfn-type (. Type (getType clojure.lang.ATaskFn))
           ifn-type (. Type (getType clojure.lang.IFn))
           kilim-session-type (. Type (getType kilim.http.HttpSession))
           pausable-type (. Type (getType kilim.Pausable))
           className (.getName (class fn))
           fn-type (. Type (getType (Class/forName className)))
           obj-type (. Type (getType Object))
           pausable-exceptions (into-array (list pausable-type (. Type (getType java.lang.Exception))))]
       
       ;; declare class
       (. cv (visit (. Opcodes V1_5) ; version
                    (+ (. Opcodes ACC_PUBLIC) (. Opcodes ACC_SUPER)) ; access
                    cname ; name
                    nil ; signature
                    "kilim/http/HttpSession" ; supername
                    (make-array String 0);interfaces
                    ))
       
       ;; generate constructors
       (doseq [^Constructor ctor (. super (getDeclaredConstructors))]
         (when-not (. Modifier (isPrivate (. ctor (getModifiers))))
           (let [ptypes (to-types (. ctor (getParameterTypes)))
                 m (new Method "<init>" (. Type VOID_TYPE) ptypes)
                 gen (new GeneratorAdapter (. Opcodes ACC_PUBLIC) m nil nil cv)]
             (. gen (visitCode))
                                        ;call super ctor
             (. gen (loadThis))
             (. gen (dup))
             (. gen (loadArgs))
             (. gen (invokeConstructor super-type m))
             
             (. gen (returnValue))
             (. gen (endMethod)))))
       
       ;; generate execute method
       (let [m (. Method (getMethod "void execute()"))
             gen (new GeneratorAdapter (. Opcodes ACC_PUBLIC) m nil pausable-exceptions cv)]
         (. gen (visitCode))
         ; build instance of the function class
         (. gen (newInstance fn-type))
         (. gen (dup))
         (. gen (invokeConstructor fn-type (new Method "<init>" (. Type VOID_TYPE) (make-array Type 0))))
         ; invoke invokeTask passing 'this' as an argument
         (. gen (checkCast itaskfn-type))
         (. gen (loadThis))
         (. gen (checkCast kilim-session-type))
         (. gen (invokeInterface itaskfn-type (new Method "invokeTask" obj-type (into-array [obj-type]))))
         (. gen (pop))
         (. gen (returnValue))
         (. gen (endMethod)))
       
       ;; end of generation
       (. cv (visitEnd))

       ;; generate bytecode, weave and load
       (let [bytecode (. cv toByteArray)
             weaver (kilim.analysis.ClassWeaver. bytecode)
             infos (.getClassInfos weaver)]
         (doseq [info infos]
              (let [pname (.replace (.className info) "/" ".")
                    _ (println (str "*** Loading weaved class " pname))]
                (. ^DynamicClassLoader (deref clojure.lang.Compiler/LOADER) (defineClass pname (.bytes info) nil))))
         (Class/forName (.replace cname "/" "."))))))
