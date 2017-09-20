(ns deercreeklabs.async-utils-test
  (:require
   [clojure.core.async :as ca]
   [clojure.test :refer [deftest is]]
   [deercreeklabs.async-utils :as au])
  #?(:cljs
     (:require-macros
      [cljs.core.async.macros :as ca])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Unit tests

(deftest test-<!?
  (au/test-async
   (ca/go
     (try
       (let [f (fn []
                 (au/go
                   (throw (ex-info "ERR!!" {:type :execution-error}))))]
         (au/<!? (f))
         (is (= :should-not-get-here :here)))
       (catch #?(:clj Exception :cljs js/Error) e
           (is (= :execution-error (-> e ex-data :type))))))))

#?(:clj
   (deftest test-<!!?
     (try
       (let [f (fn []
                 (au/go
                   (throw (ex-info "ERR!!" {:type :execution-error}))))]
         (au/<!!? (f))
         (is (= :should-not-get-here :here)))
       (catch #?(:clj Exception :cljs js/Error) e
           (is (= :execution-error (-> e ex-data :type)))))))

(deftest test-alts!?-throw
  (au/test-async
   (ca/go
     (try
       (let [f (fn []
                 (au/go
                   (throw (ex-info "ERR!!" {:type :execution-error}))))]
         (au/alts!? [(f) (ca/timeout 100)])
         (is (= :should-not-get-here :here)))
       (catch #?(:clj Exception :cljs js/Error) e
           (is (= :execution-error (-> e ex-data :type))))))))

#?(:clj
   (deftest test-alts!!?-throw
     (try
       (let [f (fn []
                 (au/go
                   (throw (ex-info "ERR!!" {:type :execution-error}))))]
         (au/alts!!? [(f) (ca/timeout 100)])
         (is (= :should-not-get-here :here)))
       (catch #?(:clj Exception :cljs js/Error) e
           (is (= :execution-error (-> e ex-data :type)))))))

(deftest test-alts!?-no-throw
  (au/test-async
   (ca/go
     (try
       (let [f (fn []
                 (au/go
                   :winner-winner))
             f-ch (f)
             [ret ch] (au/alts!? [f-ch (ca/timeout 100)])]
         (is (= f-ch ch))
         (is (= :winner-winner ret)))
       (catch #?(:clj Exception :cljs js/Error) e
           (is (= :should-not-get-here :here)))))))

#?(:clj
   (deftest test-alts!!?-no-throw
     (try
       (let [f (fn []
                 (au/go
                   :winner-winner))
             f-ch (f)
             [ret ch] (au/alts!!? [f-ch (ca/timeout 100)])]
         (is (= f-ch ch))
         (is (= :winner-winner ret)))
       (catch #?(:clj Exception :cljs js/Error) e
           (is (= :should-not-get-here :here))))))
