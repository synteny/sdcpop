(ns build
  (:require [cambada.uberjar :as uberjar]))

(defn -main [& args]
  (uberjar/-main
   "-a" "all"
   "-p" "resources"
   "--app-group-id" "sdcpop"
   "--app-artifact-id" "sdcpop"
   "--app-version" "0.0.1"
   "-m" "sdcpop.core"
   "--no-copy-source"))

(comment
  (-main))
