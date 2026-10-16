(ns consultas.main
  (:require [clojure.string :as str]
            [consultas.core :as core]))

(defn- formatar-valor [valor tipo]
  (if (= tipo :decimal)
    (String/format java.util.Locale/ROOT "%.2f" (to-array [(double valor)]))
    (str valor)))

(defn- tipo-do-campo [esquema campo]
  (second (first (filter #(= campo (first %)) esquema))))

(defn- campos-de-saida [ast esquema]
  (or (some (fn [[tipo campos]]
              (when (= tipo :selecionar) campos))
            (:estagios ast))
      (mapv first esquema)))

(defn- imprimir-registros [resultado ast esquema]
  (let [campos (campos-de-saida ast esquema)]
    (if (seq resultado)
      (doseq [registro resultado]
        (println (str/join "; "
                           (map #(formatar-valor (get registro %) (tipo-do-campo esquema %))
                                campos))))
      (println "(vazio)"))))

(defn- imprimir-agregacao [resultado agregacao esquema]
  (let [[tipo campo] agregacao
        tipo-valor (case tipo
                     :contar :inteiro
                     :media :decimal
                     :soma (tipo-do-campo esquema campo))]
    (println (formatar-valor resultado tipo-valor))))

(defn- imprimir-grupos [grupos campo-chave agregacao esquema]
  (if (seq grupos)
    (doseq [[chave valor] grupos]
      (let [[tipo campo] agregacao
            tipo-valor (case tipo
                         :contar :inteiro
                         :media :decimal
                         :soma (tipo-do-campo esquema campo))]
        (println (str (formatar-valor chave (tipo-do-campo esquema campo-chave))
                      "; " (formatar-valor valor tipo-valor)))))
    (println "(vazio)")))

(defn- imprimir-resultado [resultado ast dados]
  (if (and (map? resultado) (:erros resultado))
    (println (str "ERRO: " (first (:erros resultado))))
    (let [terminal (:terminal ast)]
      (if-not terminal
        (imprimir-registros resultado ast (:esquema dados))
        (case (first terminal)
          :agrupar-por (imprimir-grupos resultado (second terminal)
                                        (nth terminal 2) (:esquema dados))
          (:contar :soma :media) (imprimir-agregacao resultado terminal (:esquema dados)))))))

(defn- processar-comando [linha dados]
  (let [comando (str/trim linha)]
    (cond
      (str/blank? comando)
      (println "ERRO: comando vazio")

      (str/starts-with? comando "QUERY")
      (try
        (let [texto (str/trim (subs comando 5))
              ast (core/analisar texto)
              resultado (core/executar ast dados)]
          (imprimir-resultado resultado ast dados))
        (catch Exception excecao
          (println (str "ERRO: " (or (ex-message excecao) "consulta invalida")))))

      :else
      (println "ERRO: comando desconhecido"))))

(defn- executar-terminal [dados]
  (loop []
    (print "> ")
    (flush)
    (let [linha (read-line)]
      (when (and linha (not= "EXIT" (str/trim linha)))
        (processar-comando linha dados)
        (recur)))))

(defn -main [& argumentos]
  (if-let [caminho (first argumentos)]
    (try
      (executar-terminal (core/ler-csv (slurp caminho)))
      (catch Exception excecao
        (println (str "ERRO: " (or (ex-message excecao) "nao foi possivel carregar o CSV")))))
    (println "ERRO: informe o caminho do arquivo CSV")))