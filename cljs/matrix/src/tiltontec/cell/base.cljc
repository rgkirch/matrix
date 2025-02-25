(ns tiltontec.cell.base
  (:require
   #?(:cljs [tiltontec.util.base :as utm
             :refer-macros [prog1 b-when def-rmap-slots]]
      :clj  [tiltontec.util.base :as utm
             :refer :all])
   #?(:cljs [cljs.test
             :refer-macros [deftest is]])

    #?(:cljs [tiltontec.util.core
              :refer [cl-find set-ify any-ref? err ia-ref
                      make-fifo-queue fifo-empty? fifo-peek fifo-pop
                      fifo-data fifo-add rmap-setf
                      wtrx-test]
              :as ut]
       :clj [tiltontec.util.core :refer :all :as ut])))


;; --- the Cells beef -----------------------

(def +pulse+ (#?(:clj ref :cljs atom) 0))

(defn pulse-now [] @+pulse+)

(defn cells-init []
  #?(:cljs (reset! +pulse+ 0)
     :clj (dosync
           (ref-set +pulse+ 0))))

(def ^:dynamic *causation* '())
(def ^:dynamic *call-stack* nil)
(def ^:dynamic *depender*
  "*depender* let's us differentiate between the call stack and
and dependency. The problem with overloading *call-stack* with both roles
is that we miss cyclic reentrance when we use without-c-dependency in a
rule to get once behavior or just when fm-traversing to find someone"
  nil)

(def ^:dynamic *defer-changes* false)
(def +client-q-handler+ (atom nil))


(defonce unbound (gensym "unbound-cell-value"))
(defn when-bound [x]
  (when (not= x unbound) x))

(defonce uncurrent (gensym "uncurrent-formulaic-value"))

(def ^:dynamic *not-to-be* false)
(def ^:dynamic *unfinished-business* nil)
(def ^:dynamic *within-integrity* false)

;; --- debug stuff -----------------------------
(def ^:dynamic *finbiz-id* 0)
(def ^:dynamic *c-prop-depth* 0)

(def +c-debug+ (atom false))
(def ^:dynamic +stop+ (atom false)) ;; emergency brake

(defmacro pcell [tag c]
  `(println :pcell ~tag (c-slot ~c)(c-state ~c)))

;; --- procedure division ----------------------

(defn cells-reset
  ([] (cells-reset {}))
  ([options]
   (reset! +c-debug+ (:debug options false))
   (reset! +pulse+ 0)
   (reset! +client-q-handler+ (:client-queue-handler options))))

(defmacro without-c-dependency [& body]
  `(binding [*depender* nil]
      ~@body))

(defmacro cpr [& r]
  `(without-c-dependency
    (pln @+pulse+ ~@r)))

(defn +cause []
    (first *causation*))

;; --- 19000 ----------------------------------

(defn c-stopper [why]
  (reset! +stop+ why)) ;; in webserver, make sure each thread binds this freshly

(def +c-stopper+ (atom c-stopper))

(defn c-stop
  ([] (c-stop true))
  ([why]
   (@+c-stopper+ why)))

(defn c-stopped []
  @+stop+)

(defmacro un-stopped [& body]
  `(when-not @+stop+
     ~@body))

(defn ustack$ [tag] ;; debug aid
  (str tag "ustack> "(vec (map (fn [c] (:slot @c)) *call-stack*))))

(defn c-assert
  ([assertion] (when-not assertion
                 (println #_ ut/err "c-assert anon failed")))
  ([assertion fmt$ & fmt-args]
   (when-not +stop+
           (when-not assertion
                   (println #_ ut/err (str "c-assert> " fmt$ fmt-args))))))

(defn c-break [& args]
  (when-not +stop+
    (println #_ ut/err (str args))))

(defn c-warn [& args]
  (when-not +stop+
    (println (apply str "WARNING: " args))))

;; ------------------------------------------------------

(derive ::model ::object)
(derive ::cell ::object)
(derive ::c-formula ::cell)

(defn ia-type [it]
 #?(:clj (type it)
    :cljs (cond
            (instance? cljs.core.Atom it)
            (:type (meta it))
            :default (type it))))

(defn ia-type? [it typ]
  (isa? (ia-type it) typ))

(defn c-formula? [c]
  (ia-type? c ::c-formula))

(defn c-ref? [x]
  (ia-type? x ::cell))

(def-rmap-slots c-
  me slot state input? rule pulse pulse-last-changed pulse-observed
  useds users callers optimize ephemeral?
  lazy synapses synaptic?)

(defn c-value [c]
  (assert (any-ref? c))
  (cond
    (and (c-ref? c)
         (map? @c)) (:value @c)
    :else @c))

(defn c-optimized-away? [c]
  (cond
    (c-ref? c) (or (not (map? @c))
                 (not (contains? @c ::state))
                   (= :optimized-away (::state @c)))
    :else true))

(defn c-model [rc]
  (:me @rc))

(defn c-md-name [c]
  (if-let [md (c-model c)]
    (or (:name @md)
      "anon")
    "no-md"))

(defn c-slot-name [rc]
  (:slot @rc))

(defn c-value-state [rc]
  (let [v (c-value rc)]
    (cond
      (= v unbound) :unbound
      (= v uncurrent) :uncurrent
      :else :valid)))

(defn c-unbound? [rc]
  (= :unbound (c-value-state rc)))

(defn c-valid? [rc]
  (= :valid (c-value-state rc)))

;; --- dependency maintenance --------------------------------

(defn caller-ensure [used new-caller]
  ;; (trx :caller-ensure :used (c-slot used) (c-slot new-caller))
  (#?(:clj alter :cljs swap!)
   used assoc :callers (conj (c-callers used) new-caller)))

(defn caller-drop [used caller]
  (#?(:clj alter :cljs swap!)
   used assoc :callers (disj (c-callers used) caller)))

(defn unlink-from-callers [c]
  (for [caller (c-callers c)]
    (caller-drop c caller))
  (rmap-setf [:callers c] nil))

;; debug aids --------------

(defn c-slots [c k]
  (assert (c-ref? c))
  (set (map c-slot (k @c))))

;; --- defmodel rizing ---------------------

(defn md-ref? [x]
  ;;(trx :md-ref?-sees x)
  (and (any-ref? x)))
       ;; hhack (ia-type? x ::model)


;; --- mdead? ---

(defmulti mdead? (fn [me]
                   (assert (or (nil? me)
                               (md-ref? me)))
                   [(type (when me @me))]))

(defmethod mdead? :default [me]
  (if-let [m (meta me)]
    (= :dead (::state m))
    false))

;;---

#?(:cljs (set! *print-level* 3)) ;; cells are recursive data for now

(defn md-slot-owning? [class-name slot-name]
  ;; hhack
  false)

(defn c-debug [c tag])
