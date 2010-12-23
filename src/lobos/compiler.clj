;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.compiler
  "The compiler multimethod definition, an default implementation and
  some helpers functions."
  (:refer-clojure :exclude [compile replace])
  (:require (lobos [ast :as ast]))
  (use (clojure [string :only [join
                               replace
                               upper-case]])))

;;;; Helpers

(defn as-str ; taken from clojure.contrib.string
  "Like clojure.core/str, but if an argument is a keyword or symbol,
  its name will be used instead of its literal representation."
  ([] "")
  ([x] (if (instance? clojure.lang.Named x)
         (name x)
         (str x)))
  ([x & ys]
     ((fn [^StringBuilder sb more]
        (if more
          (recur (. sb (append (as-str (first more)))) (next more))
          (str sb)))
      (new StringBuilder ^String (as-str x)) ys)))

(defn as-list
  "Returns the given collection parenthesized string with its items
  separated by commas."
  [coll]
  (when (not-empty coll)
    (format "(%s)" (join ", " coll))))

(defn as-sql-keyword
  "Returns the given string, symbol or keyword as an upper-cased string
  and replace dashes with spaces."
  [s]
  (replace (-> s as-str upper-case) \- \space))

;;;; Compiler

(defmulti compile
  "Compile the given statement."
  (fn [stmt & [connection-info]]
    (let [db-spec (or connection-info (:db-spec stmt))]
      [(keyword (or (-> db-spec :subprotocol) ::standard))
       (type stmt)])))

;;;; Default compiler

;;; Expressions

(defmethod compile [::standard lobos.ast.ValueExpression]
  [expression & _]
  (let [{:keys [specification]} expression]
    (cond (keyword? specification) (str (as-sql-keyword specification) "()")
          (string? specification) (str "'" specification "'")
          :else specification)))

;;; Definitions

(defmethod compile [::standard lobos.ast.ColumnDefinition]
  [definition & _]
  (let [{:keys [cname data-type default
                identity not-null others]} definition]
    (join \space
      (concat
       [(as-str cname)
        (str (as-sql-keyword (:dtype data-type))
             (as-list (:args data-type)))]
       (when default  ["DEFAULT" (compile default)])
       (when identity ["GENERATED BY DEFAULT AS IDENTITY"])
       (when not-null ["NOT NULL"])
       others))))

(defmethod compile [::standard lobos.ast.UniqueConstraintDefinition]
  [definition & _]
  (let [{:keys [cname ctype columns]} definition
        spec (join \space
               [(as-sql-keyword ctype)
                (as-list (map as-str columns))])]
    (if cname
      (join \space [name spec])
      spec)))

;;; Statements

(defmethod compile [::standard lobos.ast.CreateSchemaStatement]
  [statement & _]
  (let [{:keys [sname elements]} statement]
    (format "CREATE SCHEMA %s \n\n%s"
            (as-str sname)
            (join "\n\n" (map compile elements)))))

(defmethod compile [::standard lobos.ast.CreateTableStatement]
  [statement & _]
  (let [{:keys [tname elements]} statement]
    (format "CREATE TABLE %s %s"
            (as-str tname)
            (or (as-list (map compile elements))
                "()"))))

(defmethod compile [::standard lobos.ast.DropStatement]
  [statement & _]
  (let [{:keys [otype oname behavior]} statement]
    (join \space
      ["DROP"
       (as-sql-keyword otype)
       (as-str oname)
       (as-sql-keyword behavior)])))
