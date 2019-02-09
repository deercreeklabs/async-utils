(ns deercreeklabs.node-test-runner
  (:require
   [cljs.nodejs :as nodejs]
   [cljs.test :as test :refer-macros [run-tests]]
   [deercreeklabs.async-utils-test]))

(nodejs/enable-util-print!)

(defn -main [& args]
  (run-tests 'deercreeklabs.async-utils-test))

(set! *main-cli-fn* -main)
