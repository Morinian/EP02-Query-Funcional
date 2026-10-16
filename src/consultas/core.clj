(ns consultas.core
  (:require [clojure.string :as str]
            [consultas.compilador :as compilador]
            [consultas.sintaxe :as sintaxe]))

(defn consulta []
  {:estagios [] :terminal nil})

(defn- acrescentar-estagio [consulta estagio]
  (update consulta :estagios conj estagio))

(defn onde [consulta expressao]
  (acrescentar-estagio consulta [:onde expressao]))

(defn selecionar [consulta campos]
  (acrescentar-estagio consulta [:selecionar campos]))

(defn ordenar-por [consulta campo ordem]
  (acrescentar-estagio consulta [:ordenar-por campo ordem]))

(defn limitar [consulta quantidade]
  (acrescentar-estagio consulta [:limitar quantidade]))

(defn analisar [texto]
  (sintaxe/analisar texto))

(defn- motivo [excecao]
  (or (ex-message excecao) "consulta invalida"))

(defn executar [consulta dados]
  (try
    (let [{:keys [pipeline terminal]} (compilador/compilar consulta (:esquema dados))
          registros (pipeline (:registros dados))]
      (if terminal (terminal registros) registros))
    (catch clojure.lang.ExceptionInfo excecao
      {:erros [(motivo excecao)]})))

(defn- tipo-csv [tipo]
  (case tipo
    "inteiro" :inteiro
    "decimal" :decimal
    "texto" :texto
    (throw (ex-info (str "tipo de campo desconhecido: " tipo) {:tipo tipo}))))

(defn- converter-valor [tipo valor]
  (case tipo
    :inteiro (Long/parseLong valor)
    :decimal (bigdec valor)
    :texto valor))

(defn ler-csv [texto]
  (let [linhas (str/split-lines texto)]
    (when (empty? linhas)
      (throw (ex-info "arquivo CSV vazio" {})))
    (let [esquema (mapv (fn [coluna]
                          (let [[nome tipo] (str/split coluna #":" -1)]
                            (when (or (str/blank? nome) (nil? tipo))
                              (throw (ex-info (str "cabecalho CSV invalido: " coluna) {})))
                            [(keyword nome) (tipo-csv tipo)]))
                        (str/split (first linhas) #";" -1))
          registros (mapv (fn [linha]
                            (let [valores (str/split linha #";" -1)]
                              (when-not (= (count valores) (count esquema))
                                (throw (ex-info "quantidade de valores CSV invalida"
                                                {:linha linha})))
                              (into {}
                                    (map (fn [[[campo tipo] valor]]
                                           [campo (converter-valor tipo valor)])
                                         (map vector esquema valores)))))
                          (rest linhas))]
      {:esquema esquema :registros registros})))