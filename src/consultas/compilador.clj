(ns consultas.compilador)

(def tipos-numericos #{:inteiro :decimal})

(defn- erro-consulta [mensagem dados]
  (throw (ex-info mensagem dados)))

(defn- mapa-esquema [esquema]
  (into {} esquema))

(defn- exigir-campo [campos campo]
  (when-not (contains? campos campo)
    (erro-consulta (str "campo inexistente: " (name campo)) {:campo campo})))

(defn- tipo-literal [valor]
  (cond
    (string? valor) :texto
    (integer? valor) :inteiro
    (number? valor) :decimal
    :else :desconhecido))

(declare compilar-expressao)

(defn- compilar-comparacao [operador campo literal]
  (partial
   (fn [op chave esperado registro]
     (let [valor (get registro chave)]
       (case op
         := (= valor esperado)
         :!= (not= valor esperado)
         :< (neg? (compare valor esperado))
         :<= (not (pos? (compare valor esperado)))
         :> (pos? (compare valor esperado))
         :>= (not (neg? (compare valor esperado))))))
   operador campo literal))

(defn- validar-comparacao [campos campo literal]
  (exigir-campo campos campo)
  (let [tipo-campo (get campos campo)
        tipo-valor (tipo-literal literal)
        compativeis? (or (and (contains? tipos-numericos tipo-campo)
                              (contains? tipos-numericos tipo-valor))
                         (= tipo-campo tipo-valor))]
    (when-not compativeis?
      (erro-consulta (str "comparacao entre " (name tipo-campo) " e "
                          (name tipo-valor) " no campo " (name campo))
                     {:campo campo :tipo-campo tipo-campo :tipo-valor tipo-valor}))))

(defn compilar-expressao [expressao campos]
  (let [[tipo campo-or-esquerda valor-or-direita] expressao]
    (case tipo
      :e (let [esquerda (compilar-expressao campo-or-esquerda campos)
               direita (compilar-expressao valor-or-direita campos)]
           (fn [registro] (and (esquerda registro) (direita registro))))
      :ou (let [esquerda (compilar-expressao campo-or-esquerda campos)
                direita (compilar-expressao valor-or-direita campos)]
            (fn [registro] (or (esquerda registro) (direita registro))))
      :nao (let [predicado (compilar-expressao campo-or-esquerda campos)]
             (fn [registro] (not (predicado registro))))
            (:= :!= :< :<= :> :>=)
            (do
         (validar-comparacao campos campo-or-esquerda valor-or-direita)
          (compilar-comparacao tipo campo-or-esquerda valor-or-direita)))))

(defn- validar-agregacao-campo [campos operacao campo]
  (exigir-campo campos campo)
  (when-not (contains? tipos-numericos (get campos campo))
    (erro-consulta (str operacao " exige campo numerico: " (name campo))
                   {:campo campo :operacao operacao})))

(defn compilar-agregacao [agregacao campos]
  (let [[tipo campo] agregacao]
    (case tipo
      :contar (fn [registros]
                (reduce (fn [total _] (inc total)) 0 registros))
      :soma (do
              (validar-agregacao-campo campos "soma" campo)
              (fn [registros]
                (reduce (fn [total registro] (+ total (get registro campo)))
                        0 registros)))
      :media (do
               (validar-agregacao-campo campos "media" campo)
               (fn [registros]
                 (let [[soma quantidade]
                       (reduce (fn [[total n] registro]
                                 [(+ total (get registro campo)) (inc n)])
                               [0 0] registros)]
                    (if (zero? quantidade)
                      0
                      (/ (double soma) quantidade))))))))

(defn- compilar-estagio [estagio campos]
  (let [[tipo argumento argumento-2] estagio]
    (case tipo
      :onde (let [predicado (compilar-expressao argumento campos)]
              (partial filter predicado))
      :ordenar-por (do
                     (exigir-campo campos argumento)
                       (when-not (contains? #{:asc :desc} argumento-2)
                         (erro-consulta (str "ordem invalida: " argumento-2)
                                        {:ordem argumento-2}))
                       (if (= argumento-2 :desc)
                         (partial sort-by argumento #(compare %2 %1))
                         (partial sort-by argumento)))
      :limitar (partial take argumento)
      :selecionar (do
                    (reduce (fn [_ campo] (exigir-campo campos campo))
                            nil argumento)
              (partial map #(select-keys % argumento))))))

(defn- compilar-terminal [terminal campos]
  (when terminal
    (let [[tipo campo agregacao] terminal]
      (case tipo
        (:contar :soma :media) (compilar-agregacao terminal campos)
        :agrupar-por (do
                       (exigir-campo campos campo)
                       (let [agregar (compilar-agregacao agregacao campos)]
                         (fn [registros]
                           (->> registros
                                (group-by campo)
                                (map (fn [[chave grupo]]
                                       [chave (agregar grupo)]))
                                (sort-by first compare)
                                    vec))))))))

(defn compilar [consulta esquema]
  (let [campos (mapa-esquema esquema)
        estagios (:estagios consulta)
        indices-selecao (keep-indexed
                         (fn [indice [tipo _]]
                           (when (= tipo :selecionar) indice))
                         estagios)]
    (when (and (seq indices-selecao)
               (not= (last indices-selecao) (dec (count estagios))))
      (erro-consulta "selecionar deve ser o ultimo estagio" {}))
    (when (and (:terminal consulta) (seq indices-selecao))
      (erro-consulta "consulta nao pode combinar selecionar e terminal" {}))
    (let [funcoes (mapv #(compilar-estagio % campos) estagios)
          pipeline (reduce (fn [funcao estagio] (comp estagio funcao))
                           identity funcoes)
          terminal (compilar-terminal (:terminal consulta) campos)]
      {:pipeline pipeline
       :terminal terminal
       :campos campos})))