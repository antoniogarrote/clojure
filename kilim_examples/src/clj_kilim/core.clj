(ns clj-kilim.core
  (:import [kilim Mailbox Pausable Task])
  (:use [clojure.core.match.core :only [match match-1]]))


(defn simple-task
  ([] (let [mbox (new kilim.Mailbox)
            actor (pausable [] (let [rec (.get mbox)] (println (str "RECEIVED " rec))))]
        (.start actor)
        (.putnb mbox "hola"))))
;(simple-task)

(defn ^{:pausable true} receive
  ([^kilim.Mailbox mbox]
     (.get mbox)))

(defn actor-mbox
  ([i] (let [mbox (new kilim.Mailbox)
             actor ^{:pausable true} (fn [] (loop [rec (.get mbox)] (println (str "RECEIVED " rec " - " i)) (recur (.get mbox))))]
         (.start actor)
         mbox)))

(let [m (actor-mbox 0)]
  (.putnb m "hola"))


(defn actor-mbox-2
  ([i] (let [mbox (new kilim.Mailbox)
             actor ^{:pausable true} (fn [] (loop [rec (receive mbox)] (println (str "RECEIVED " rec " - " i)) (recur (.get mbox))))]
         (.start actor)
         mbox)))


(defn multitest
  ([] (let [boxes (map (fn [x] (actor-mbox-2 x)) (range 0 1000))]
        (loop [mboxes (cycle boxes)]
          (.putnb (first mboxes) "hola!")
          (recur (rest mboxes))))))
;(multitest)

(defn multitest2
  ([] (let [boxes (map (fn [x] (actor-mbox-2 x)) (range 0 1000))]
        (loop [mboxes (cycle boxes)]
          (.putnb (first mboxes) "hola!")
          (recur (rest mboxes))))))

;;; chain example

(defn chain
  ([prev-mbox]
     (chain prev-mbox (kilim.Mailbox.)))
  ([prev-mbox next-mbox]
     (let [node ^{:pausable true} (fn [] (let [rec (.get prev-mbox)]
                                          (if (nil? next-mbox)
                                            (println (str rec "world"))
                                            (.put next-mbox (str "hello " rec)))))]
       (.start node)
       next-mbox)))

(defn chain-example
  ([chain-length]
     (let [initial-mbox (kilim.Mailbox.)
           prev-last-mbox (reduce (fn [prev-mbox _] (chain prev-mbox))
                                  initial-mbox
                                  (range 0 (dec chain-length)))]
       (chain prev-last-mbox nil)
       (.putnb initial-mbox "hello "))))

;(chain-example 10)


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
     (let [exitmb (kilim.Mailbox.)]
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
                     (println (str "           Task #" 1 " waking up"))
                     1))
           task2 ^{:pausable true}
           (fn [] (do (println (str "Task #" 2 " going to sleep ..."))
                     (kilim.Task/sleep 1000)
                     (println (str "           Task #" 2 " waking up"))
                     2))
           group (kilim.TaskGroup.)]
       (.add group (.start task1))
       (.add group (.start task2))
       (.joinb group)
       (println (str "finished -> " (.results group))))))

;(group-example)

;; test yield

(defn ^{:generator true} fib [g]
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
                                              (let [resp (kilim.http.HttpResponse.)
                                                    pw (java.io.PrintWriter. (.getOutputStream resp))]
                                                (.append pw (str "<html><body><h1>Request!</h1> <br/> <p>Path: " (.uriPath req) "</p></body></html>"))
                                                (.flush pw)
                                                (.sendResponse this resp)
                                                (if (.keepAlive req)
                                                  (recur)
                                                  (println "ending execution"))))))))


;(kilim.http.HttpServer. 7293 http-handler)



;; pmap
(defn pmap-kilim
  ([f c]
     (let [results (make-array Object (count c))
           group (kilim.TaskGroup.)]
       (reduce
        (fn [i x] (.add group (.start ^{:pausable true} (fn [] (aset results i (f x))))) (inc i))
        0
        c)
       (.joinb group)
       results)))

(time (do (println (count (pmap (fn [x] (* x 2)) (range 0 100000)))) (println "finished!")))
(time (do (println (count (pmap-kilim (fn [x] (* x 2)) (range 0 100000)))) (println "finished!")))
(pmap-kilim (fn [x] (* x 2)) [1 2 3])



(defn spawn
  ([^clojure.lang.ITaskFn f] (let [m ^clojure.kilim.SelectiveMailbox (clojure.kilim.SelectiveMailbox.)
             fw ^{:pausable true} (fn [] (f m))]
         (.start fw)
         m)))

(def ^:dynamic *mbox* (kilim.Mailbox.))

(defn ^{:pausable true} receive
  ([^kilim.Mailbox mbox]
     (.get mbox)))

(defn receive-np
  ([] (.getb *mbox*)))

(defn ^{:pausable true} selective-receive
  [^clojure.kilim.SelectiveMailbox mbox f]
    (loop []
      (let [res (.get mbox)]
        (if (f res)
          (do
            (.accept mbox)
            res)
          (do
            (.reject mbox res)
            (recur))))))


(defn self
  ([] *mbox*))

(defn !
  ([^kilim.Mailbox mbox
    ^java.lang.Object msg]
     (.putnb mbox msg)))

(defn ^{:pausable true} handler [mbox] (let [[res from] (receive mbox)] (println (str "READ " res)) (! from "pong")))


(let [pid (spawn handler)]
  (println (str "GOT PID " pid))
  (! pid ["hola" (self)])
  (println (str "PONG? " (receive-np))))

(defn ^{:pausable true} handler [mbox]
  (loop []
    (let [res (selective-receive mbox odd?)]
      (println (str "GOT SOMETHING " res))
      (recur))))

(let [pid (spawn handler)]
  (! pid 2)
  (! pid 3))


(let [a 1 b 2 c 3]
  (match-1 ["hola"]
         [_ 1 _] :uno
         [_ 2 _] :dos
         [_ 3 _] :tres

         [_] :other))


(defn ^{:pausable true} make-relay [^kilim.Mailbox self
                                    ^kilim.Mailbox next
                                    ^kilim.Mailbox final]
  (loop [k (receive self)]
    (if (> k 0)
      (do
        (! next (dec k))
        (recur (.get self)))
      (do
        (! next (dec k))
        (when (not (nil? final))
          (! final true))))))


(defn make-loop [n k]
  (let [^kilim.Mailbox first-mailbox (kilim.Mailbox.)
        ^kilim.Mailbox final-mailbox (kilim.Mailbox.)]
    (loop [current  first-mailbox
           n n]
      (if (> n 1)
        (recur
         (spawn (pausable [^kilim.Mailbox self]
                          (make-relay self current nil)))
         (dec n))
        (do (spawn (pausable [^kilim.Mailbox _]
                             (make-relay first-mailbox current final-mailbox)))
            (! first-mailbox k)
            (.getb final-mailbox))))))


;(time (make-loop 100000 1000000))
