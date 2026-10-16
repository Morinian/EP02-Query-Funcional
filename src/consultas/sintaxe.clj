(ns consultas.sintaxe
  (:require [clojure.string :as str]))

(defn- erro-sintaxe [mensagem dados]
  (throw (ex-info mensagem dados)))

(defn- palavra-inicial? [caractere]
  (or (Character/isLetter caractere) (= caractere \_)))

(defn- palavra-caractere? [caractere]
  (or (Character/isLetterOrDigit caractere)
      (= caractere \_)
      (= caractere \-)))

(defn- numero-caractere? [caractere]
  (Character/isDigit caractere))

(defn- tokenizar [texto]
  (loop [indice 0
         tokens []]
    (if (>= indice (count texto))
      tokens
      (let [caractere (.charAt texto indice)]
        (cond
          (Character/isWhitespace caractere)
          (recur (inc indice) tokens)

          (= caractere \")
          (let [fim (.indexOf texto "\"" (inc indice))]
            (when (neg? fim)
              (erro-sintaxe "texto sem aspas de fechamento" {:indice indice}))
            (recur (inc fim)
                   (conj tokens {:tipo :literal
                                 :valor (subs texto (inc indice) fim)})))

          (contains? #{\| \, \( \)} caractere)
          (recur (inc indice)
                 (conj tokens {:tipo :simbolo :valor (str caractere)}))

          (contains? #{\= \! \< \>} caractere)
          (let [duplo? (and (< (inc indice) (count texto))
                            (= \= (.charAt texto (inc indice)))
                            (not= caractere \=))
                operador (if duplo?
                           (subs texto indice (+ indice 2))
                           (str caractere))]
            (when (and (= caractere \!) (not duplo?))
              (erro-sintaxe "operador invalido" {:indice indice}))
            (recur (+ indice (count operador))
                   (conj tokens {:tipo :operador :valor operador})))

          (or (numero-caractere? caractere)
              (and (= caractere \-)
                   (< (inc indice) (count texto))
                   (numero-caractere? (.charAt texto (inc indice)))))
          (let [inicio indice
                inicio-digitos (if (= caractere \-) (inc indice) indice)
                fim-inteiro (loop [fim inicio-digitos]
                              (if (and (< fim (count texto))
                                       (numero-caractere? (.charAt texto fim)))
                                (recur (inc fim))
                                fim))
                decimal? (and (< fim-inteiro (count texto))
                              (= \. (.charAt texto fim-inteiro))
                              (< (inc fim-inteiro) (count texto))
                              (numero-caractere? (.charAt texto (inc fim-inteiro))))
                fim (if decimal?
                      (loop [fim (inc fim-inteiro)]
                        (if (and (< fim (count texto))
                                 (numero-caractere? (.charAt texto fim)))
                          (recur (inc fim))
                          fim))
                      fim-inteiro)
                valor (subs texto inicio fim)]
            (recur fim (conj tokens {:tipo :numero :valor valor})))

          (palavra-inicial? caractere)
          (let [fim (loop [fim (inc indice)]
                      (if (and (< fim (count texto))
                               (palavra-caractere? (.charAt texto fim)))
                        (recur (inc fim))
                        fim))]
            (recur fim
                   (conj tokens {:tipo :palavra
                                 :valor (subs texto indice fim)})))

          :else
          (erro-sintaxe (str "caractere inesperado: " caractere)
                        {:indice indice}))))))

(defn- token-e? [token tipo valor]
  (and token (= tipo (:tipo token)) (= valor (:valor token))))

(defn- exigir-token [tokens tipo valor esperado]
  (if (token-e? (first tokens) tipo valor)
    (rest tokens)
    (erro-sintaxe (str "esperado " esperado) {:tokens tokens})))

(declare analisar-ou)

(defn- analisar-literal [tokens]
  (let [{:keys [tipo valor]} (first tokens)]
    (case tipo
      :literal [valor (rest tokens)]
      :numero (if (str/includes? valor ".")
                [(bigdec valor) (rest tokens)]
                [(Long/parseLong valor) (rest tokens)])
      (erro-sintaxe "literal esperado" {:tokens tokens}))))

(defn- analisar-primaria [tokens]
  (let [token (first tokens)]
    (cond
      (token-e? token :palavra "nao")
      (let [[expressao restantes] (analisar-primaria (rest tokens))]
        [[:nao expressao] restantes])

      (token-e? token :simbolo "(")
      (let [[expressao restantes] (analisar-ou (rest tokens))]
        [expressao (exigir-token restantes :simbolo ")" "')'")])

      (= :palavra (:tipo token))
      (let [campo (keyword (:valor token))
            operador-token (second tokens)
            operador (get {"=" := "!=" :!= "<" :< "<=" :<= ">" :> ">=" :>=}
                          (:valor operador-token))]
        (when-not operador
          (erro-sintaxe "operador de comparacao esperado" {:tokens tokens}))
        (let [[literal restantes] (analisar-literal (nnext tokens))]
          [[operador campo literal] restantes]))

      :else
      (erro-sintaxe "expressao esperada" {:tokens tokens}))))

(defn- analisar-e [tokens]
  (let [[esquerda restantes] (analisar-primaria tokens)]
    (if (token-e? (first restantes) :palavra "e")
      (let [[direita finais] (analisar-e (rest restantes))]
        [[:e esquerda direita] finais])
      [esquerda restantes])))

(defn- analisar-ou [tokens]
  (let [[esquerda restantes] (analisar-e tokens)]
    (if (token-e? (first restantes) :palavra "ou")
      (let [[direita finais] (analisar-ou (rest restantes))]
        [[:ou esquerda direita] finais])
      [esquerda restantes])))

(defn- exigir-palavra [tokens palavra]
  (if (token-e? (first tokens) :palavra palavra)
    (rest tokens)
    (erro-sintaxe (str "esperado " palavra) {:tokens tokens})))

(defn- analisar-campo [tokens]
  (if (= :palavra (:tipo (first tokens)))
    [(keyword (:valor (first tokens))) (rest tokens)]
    (erro-sintaxe "campo esperado" {:tokens tokens})))

(defn- analisar-agregacao [tokens]
  (cond
    (token-e? (first tokens) :palavra "contar")
    [[:contar] (rest tokens)]

    (token-e? (first tokens) :palavra "soma")
    (let [[campo restantes] (analisar-campo (rest tokens))]
      [[:soma campo] restantes])

    (token-e? (first tokens) :palavra "media")
    (let [[campo restantes] (analisar-campo (rest tokens))]
      [[:media campo] restantes])

    :else
    (erro-sintaxe "agregacao esperada" {:tokens tokens})))

(defn- analisar-elemento [tokens]
  (cond
    (token-e? (first tokens) :palavra "onde")
    (let [[expressao restantes] (analisar-ou (rest tokens))]
      [:estagio [:onde expressao] restantes])

    (token-e? (first tokens) :palavra "ordenar")
    (let [apos-por (exigir-palavra (rest tokens) "por")
          [campo restantes] (analisar-campo apos-por)
          descendente? (token-e? (first restantes) :palavra "desc")
          finais (if descendente? (rest restantes) restantes)]
      [:estagio [:ordenar-por campo (if descendente? :desc :asc)] finais])

    (token-e? (first tokens) :palavra "limitar")
    (let [token (second tokens)]
      (when-not (and (= :numero (:tipo token))
                     (not (str/includes? (:valor token) ".")))
        (erro-sintaxe "inteiro esperado depois de limitar" {:tokens tokens}))
      [:estagio [:limitar (Long/parseLong (:valor token))] (nnext tokens)])

    (token-e? (first tokens) :palavra "selecionar")
    (let [[primeiro restantes] (analisar-campo (rest tokens))
          [campos finais] (loop [campos [primeiro]
                                tokens restantes]
                           (if (token-e? (first tokens) :simbolo ",")
                             (let [[campo prox] (analisar-campo (rest tokens))]
                               (recur (conj campos campo) prox))
                             [campos tokens]))]
      [:final [:selecionar campos] finais])

    (token-e? (first tokens) :palavra "contar")
    [:final [:contar] (rest tokens)]

    (token-e? (first tokens) :palavra "soma")
    (let [[campo restantes] (analisar-campo (rest tokens))]
      [:final [:soma campo] restantes])

    (token-e? (first tokens) :palavra "media")
    (let [[campo restantes] (analisar-campo (rest tokens))]
      [:final [:media campo] restantes])

    (token-e? (first tokens) :palavra "agrupar")
    (let [apos-por (exigir-palavra (rest tokens) "por")
          [campo restantes] (analisar-campo apos-por)
          apos-com (exigir-palavra restantes "com")
          [agregacao finais] (analisar-agregacao apos-com)]
      [:final [:agrupar-por campo agregacao] finais])

    :else
    (erro-sintaxe "elemento de consulta esperado" {:tokens tokens})))

(defn analisar [texto]
  (let [tokens (tokenizar texto)]
    (if (empty? tokens)
      {:estagios [] :terminal nil}
      (loop [restantes tokens
             estagios []]
        (let [[classe no proximos] (analisar-elemento restantes)]
          (if (= classe :final)
            (do
              (when (seq proximos)
                (erro-sintaxe "elemento final deve ser o ultimo" {:tokens proximos}))
              (if (= :selecionar (first no))
                {:estagios (conj estagios no) :terminal nil}
                {:estagios estagios :terminal no}))
            (cond
              (empty? proximos)
              {:estagios (conj estagios no) :terminal nil}

              (token-e? (first proximos) :simbolo "|")
              (recur (rest proximos) (conj estagios no))

              :else
              (erro-sintaxe "tokens inesperados" {:tokens proximos}))))))))