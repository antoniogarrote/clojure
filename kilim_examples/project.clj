(defproject clj-kilim "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [match "0.2.0-SNAPSHOT"]]
  :jvm-opts ["-Xmx4g -Dkilim.Scheduler.numThreads=10"]
  :dev-dependencies [[swank-clojure "1.3.0-SNAPSHOT"]])
