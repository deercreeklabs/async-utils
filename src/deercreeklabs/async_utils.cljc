(ns deercreeklabs.async-utils
  (:require
   #?(:cljs [cljs.test])
   [clojure.core.async :as ca]
   [clojure.core.async.impl.protocols :as cap]
   [schema.core :as s :include-macros true])
  #?(:cljs
     (:require-macros
      [cljs.core.async.macros :as ca]
      deercreeklabs.async-utils)))

(def Channel (s/protocol cap/Channel))

;;;;;;;;;;;;;;;;;;;; Macro-writing utils ;;;;;;;;;;;;;;;;;;;;

;; From: (str "http://blog.nberger.com.ar/blog/2015/09/18/"
"more-portable-complex-macro-musing/"
(defn- cljs-env?
  "Take the &env from a macro, and return whether we are expanding into cljs."
  [env]
  (boolean (:ns env)))

(defmacro if-cljs
  "Return then if we are generating cljs code and else for Clojure code.
  https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
  [then else]
  (if (cljs-env? &env) then else))

;;;;;;;;;;;;;;;;;;;; core.async utils ;;;;;;;;;;;;;;;;;;;;


(defmacro go-helper* [ex-type body]
  `(try
     ~@body
     (catch ~ex-type e#
       e#)))

(defmacro go [& body]
  `(if-cljs
    (cljs.core.async.macros/go
      (go-helper* :default ~body))
    (clojure.core.async/go
      (go-helper* Exception ~body))))

(defn check [v]
  (if (instance? #?(:cljs js/Error :clj Throwable) v)
    (throw v)
    v))

(defmacro <? [ch-expr]
  `(check (clojure.core.async/<! ~ch-expr)))

(defmacro alts? [chs-expr]
  `(let [[v# ch#] (clojure.core.async/alts! ~chs-expr)]
     [(check v#) ch#]))

(defmacro <?? [ch-expr]
  `(check (clojure.core.async/<!! ~ch-expr)))

(defmacro alts?? [chs-expr]
  `(let [[v# ch#] (clojure.core.async/alts!! ~chs-expr)]
     [(check v#) ch#]))

;;;;;;;;;;;;;;;;;;;; Async test helpers ;;;;;;;;;;;;;;;;;;;;

(s/defn test-async* :- s/Any
  [timeout-ms :- s/Num
   test-ch :- Channel]
  (ca/go
    (let [[ret ch] (ca/alts! [test-ch (ca/timeout timeout-ms)])]
      (if (= test-ch ch)
        ret
        (throw (ex-info (str "Async test did not complete within "
                             timeout-ms " ms.")
                        {:type :test-failure
                         :subtype :async-test-timeout
                         :timeout-ms timeout-ms}))))))

(s/defn test-async :- s/Any
  ([test-ch :- Channel]
   (test-async 1000 test-ch))
  ([timeout-ms :- s/Num
    test-ch :- Channel]
   (let [ch (test-async* timeout-ms test-ch)]
     #?(:clj (check (ca/<!! ch))
        :cljs (cljs.test/async
               done (ca/take! ch (fn [ret]
                                   (try
                                     (check ret)
                                     (finally
                                       (done))))))))))
