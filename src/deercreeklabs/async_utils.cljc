(ns deercreeklabs.async-utils
  (:require
   #?(:cljs [cljs.test])
   [clojure.core.async :as ca]
   [clojure.core.async.impl.protocols :as cap]
   [clojure.test :as t])
  #?(:cljs
     (:require-macros
      deercreeklabs.async-utils)))

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

(defn channel? [x]
  (satisfies? cap/Channel x))

(defmacro go-helper* [ex-type body]
  `(try
     ~@body
     (catch ~ex-type e#
       e#)))

(defmacro go [& body]
  `(if-cljs
       (clojure.core.async/go
         (go-helper* :default ~body))
     (clojure.core.async/go
       (go-helper* Exception ~body))))

(defn check [v]
  (if (instance? #?(:cljs js/Error :clj Throwable) v)
    (throw v)
    v))

(defmacro <? [ch-expr]
  `(check (clojure.core.async/<! ~ch-expr)))

(defmacro alts? [chs-expr & opts]
  `(let [[v# ch#] (clojure.core.async/alts! ~chs-expr ~@opts)]
     [(check v#) ch#]))

(defmacro <?? [ch-expr]
  `(check (clojure.core.async/<!! ~ch-expr)))

(defmacro alts?? [chs-expr & opts]
  `(let [[v# ch#] (clojure.core.async/alts!! ~chs-expr ~@opts)]
     [(check v#) ch#]))

;;;;;;;;;;;;;;;;;;;; Async test helpers ;;;;;;;;;;;;;;;;;;;;

(defn test-async*
  [timeout-ms test-ch *fns]
  (deercreeklabs.async-utils/go
    (let [[ret ch] (ca/alts! [test-ch (ca/timeout timeout-ms)])]
      (if (= test-ch ch)
        (check ret)
        (do
         (when *fns (doseq [f @*fns] (f)))
         (throw (ex-info (str "Async test did not complete within "
                             timeout-ms " ms.")
                        {:type :test-failure
                         :subtype :async-test-timeout
                         :timeout-ms timeout-ms})))))))

(defn test-async
  ([test-ch]
   (test-async 1000 test-ch))
  ([timeout-ms test-ch]
   (test-async 1000 test-ch nil))
  ([timeout-ms test-ch *fns]
   (let [ch (test-async* timeout-ms test-ch *fns)]
     #?(:clj (<?? ch)
        :cljs (cljs.test/async
               done (ca/take! ch (fn [ret]
                                   (try
                                     (check ret)
                                     (finally
                                       (done))))))))))

(defmacro <catch-msg-helper* [taker ch-expr]
  `(let [ret# ~ch-expr]
     (if (channel? ret#)
       (ex-message (~taker ret#))
       "Given expression did not evaluate to a channel.")))

(defmacro <catch-msg! [ch-expr]
  `(<catch-msg-helper* clojure.core.async/<! ~ch-expr))

#?(:clj
   (defmacro <catch-msg!! [ch-expr]
     `(<catch-msg-helper* clojure.core.async/<!! ~ch-expr)))

(defmacro <is-thrown-with-msg? [regex & form]
  (let [expected (str "Throws with message that matches #\"" regex "\"")]
    `(let [ex-msg# (<catch-msg! ~@form)
           actual# (str "Threw with mesage `" ex-msg#)
           ret# (re-find ~regex ex-msg#)]
       (if ret#
         (clojure.test/do-report {:type :pass
                                  :expected ~expected
                                  :actual actual#})
         (clojure.test/do-report {:type :fail
                                  :expected ~expected
                                  :actual actual#}))
       ret#)))
