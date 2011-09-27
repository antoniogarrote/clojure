(ns clj-kilim.core
  (:import [kilim Mailbox Pausable Task]))


(defn test-1
  ([] (let [^kilim.Mailbox mbox (new kilim.Mailbox)
            actor ^{:pausable true} (fn [] (let [rec (.get mbox)] (println (str "RECEIVED " rec))))]
        (.start actor)
        (.putnb mbox "hola"))))


(defn ^{:pausable true} receive
  ([^kilim.Mailbox mbox]
     (.get mbox)))


(defn actor-mbox
  ([i] (let [^kilim.Mailbox mbox (new kilim.Mailbox)
             actor ^{:pausable true} (fn [] (loop [rec (.get mbox)] (println (str "RECEIVED " rec " - " i)) (recur (.get mbox))))]
         (.start actor)
         mbox)))

;(let [m (actor-mbox 0)]
;  (.putnb m "hola"))


(defn actor-mbox-2
  ([i] (let [^kilim.Mailbox mbox (new kilim.Mailbox)
             actor ^{:pausable true} (fn [] (loop [rec (receive mbox)] (println (str "RECEIVED " rec " - " i)) (recur (.get mbox))))]
         (.start actor)
         mbox)))


(defn multitest
  ([] (let [boxes (map (fn [x] (actor-mbox-2 x)) (range 0 1000))]
        (loop [mboxes (cycle boxes)]
          (.putnb (first mboxes) "hola!")
          (recur (rest mboxes))))))
;(multitest)

;;; chain example

(defn chain
  ([^kilim.Mailbox prev-mbox]
     (chain prev-mbox (kilim.Mailbox.)))
  ([^kilim.Mailbox prev-mbox ^kilim.Mailbox next-mbox]
     (let [node ^{:pausable true} (fn [] (let [rec (.get prev-mbox)]
                                          (if (nil? next-mbox)
                                            (println (str rec "world"))
                                            (.put next-mbox (str "hello " rec)))))]
       (.start node)
       next-mbox)))

(defn chain-example
  ([chain-length]
     (let [^kilim.Mailbox initial-mbox (kilim.Mailbox.)
           ^kilim.Mailbox prev-last-mbox (reduce (fn [prev-mbox _] (chain prev-mbox))
                                                 initial-mbox
                                                 (range 0 (dec chain-length)))]
       (chain prev-last-mbox nil)
       (.putnb initial-mbox "hello "))))

;(chain-example 5000)


;; Timed task

(defn timed-task
  ([i exitmb]
     (let [task ^{:pausable true}
           (fn [] (do (println (str "Task #" i " going to sleep ..."))
                     (kilim.Task/sleep 2000)
                     (println (str "           Task #" i " waking up"))))]
       (.. task start (informOnExit exitmb)))))

(defn timed-task-example
  ([num-tasks]
     (let [^kilim.Mailbox exitmb (kilim.Mailbox.)]
       (doseq [i (range 0 num-tasks)]
         (timed-task i exitmb))
       (.getb exitmb)
       (println (str "finished...")))))

;(timed-task-example 1000)


;; Group example

(defn group-example
  ([]
     (let [task1 ^{:pausable true}
           (fn [] (do (println (str "Task #" 1 " going to sleep ..."))
                     (kilim.Task/sleep 1000)
                     (println (str "           Task #" 1 " waking up"))))
           task2 ^{:pausable true}
           (fn [] (do (println (str "Task #" 2 " going to sleep ..."))
                     (kilim.Task/sleep 1000)
                     (println (str "           Task #" 2 " waking up"))))
           group (kilim.TaskGroup.)]
       (.add group (.start task1))
       (.add group (.start task2))
       (.joinb group)
       (println "finished"))))

;(group-example)

;; test yield

(defn ^{:generator true} fib [^kilim.Generator g]
  (. g yield java.math.BigInteger/ZERO)
  (loop [i java.math.BigInteger/ZERO
         j java.math.BigInteger/ONE]
    (. g yield j)
    (recur j (.add i j))))



(defn test-generator
  ([] (let [g (kilim-generator-seq fib)]
        (doseq [n (take 10000 g)]
          (println (str "GOT " n))))))

;(test-generator)
(take 10 (kilim-generator-seq fib))

;; Simple HTTP server

(def http-handler
  (kilim-http-handler ^{:pausable true} (fn [^kilim.http.HttpSession this]
                                          (let [req (kilim.http.HttpRequest.)]
                                            (loop []                                            
                                              (.readRequest this req)
                                              (println (str "received something! -> " req))
                                              (let [^kilim.http.HttpResponse resp (kilim.http.HttpResponse.)
                                                    pw (java.io.PrintWriter. (.getOutputStream resp))]
                                                (.append pw (str "<html><body><h1>Request!</h1> <br/> <p>Path: " (.uriPath req) "</p></body></html>"))
                                                (.flush pw)
                                                (.sendResponse this resp)
                                                (if (.keepAlive req)
                                                  (recur)
                                                  (println "ending execution"))))))))


;(kilim.http.HttpServer. 7293 http-handler)


