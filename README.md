
Paula Martins, Eric Donato e Matheus Batista

# Query builder funcional em Clojure

## Como executar

Com o Clojure CLI instalado, execute na raiz do projeto:

```powershell
clojure -M:run dados/filmes.csv
```

Digite `QUERY` seguido de uma consulta. `EXIT` encerra o programa; o fim da entrada tambem encerra.

## Namespaces

- `consultas.main`: le o CSV, controla o terminal interativo e imprime os resultados.
- `consultas.sintaxe`: transforma o texto da consulta em AST por meio de analise recursiva.
- `consultas.compilador`: confere a AST contra o esquema e transforma seus nos em funcoes.
- `consultas.core`: le o CSV em registros, oferece o builder e a funcao `executar`.

## Forma da AST

Uma consulta e um mapa com `:estagios` e `:terminal`. Os estagios sao vetores. Um terminal, quando existe, fica em `:terminal`; `selecionar` e representado como o ultimo estagio.

```clojure
{:estagios [[:onde [:e [:= :genero "drama"] [:>= :nota 8]]]
            [:ordenar-por :nota :desc]
            [:selecionar [:titulo :nota]]]
 :terminal nil}
```

Exemplo de consulta terminal:

```clojure
{:estagios [[:onde [:= :genero "drama"]]]
 :terminal [:media :duracao]}
```

Os estagios aceitos sao `:onde`, `:ordenar-por`, `:limitar` e `:selecionar`. Os terminais sao `[:contar]`, `[:soma campo]`, `[:media campo]` e `[:agrupar-por campo agregacao]`.

## Mapas e imutabilidade

Um registro e um mapa. Palavras-chave, como `:ano`, podem buscar valores no mapa:

```clojure
(def filme {:titulo "Bacurau" :ano 2019})
(:ano filme) ; => 2019
```

Mapas e vetores sao imutaveis. `assoc`, `update` e `conj` devolvem uma colecao nova, sem alterar a anterior. Por isso a AST pode ser impressa e comparada com `=`. Ela contem dados, nao funcoes.

## Builder

O builder recebe uma consulta e devolve outra com um estagio a mais. A consulta anterior permanece igual:

```clojure
(require '[consultas.core :as core])

(def base (core/consulta))
(def filtrada (core/onde base [:= :campo "valor"]))
(def duas (core/limitar filtrada 2))

(= duas (core/analisar "onde campo = \"valor\" | limitar 2"))
;; => true
```

Assim, o builder e o analisador produzem o mesmo formato de AST.

## Analise e compilacao

Expressoes podem conter outras expressoes. Por exemplo, `[:e a b]` tem duas expressoes menores. O analisador trata essa estrutura recursivamente: cada chamada recebe os tokens restantes e devolve a parte reconhecida e o que falta analisar.

Depois, o compilador percorre a AST e cria funcoes para os estagios. O `case` escolhe o que fazer conforme o tipo do no. Sem um ramo padrao, um tipo desconhecido gera erro em vez de ser aceito silenciosamente. Essa escolha acontece na compilacao, nao para cada registro.

Um ramo pode aceitar mais de um valor:

```clojure
(defn tipo-de [operador]
  (case operador
    (:e :ou :nao) :logico
    (:= :!= :< :> :<= :>=) :comparacao))

(tipo-de :ou) ; => :logico
```

Como nao ha ramo padrao, um valor nao listado faz `case` gerar erro:

```clojure
(case :x :a 1 :b 2)
;; IllegalArgumentException: No matching clause: :x
```

## Funcoes e composicao

`partial` fixa argumentos e devolve uma funcao que espera os restantes:

```clojure
((partial take 3) [10 20 30 40]) ; => (10 20 30)
```

`comp` combina funcoes e aplica da direita para a esquerda. Como a consulta e lida da esquerda para a direita, os estagios sao compostos na ordem adequada:

```clojure
((reduce (fn [funcao estagio] (comp estagio funcao))
     identity
     [inc (partial * 10)])
 1)
;; => 20
```

Primeiro `inc` transforma `1` em `2`; depois a funcao parcial multiplica o resultado por `10`.

## Agregacoes com reduce

`reduce` percorre uma colecao e devolve um acumulador. Esse acumulador pode guardar, por exemplo, soma e quantidade:

```clojure
(reduce (fn [[soma quantidade] valor]
      [(+ soma valor) (inc quantidade)])
    [0 0]
    [4 6])
;; => [10 2]
```

Esse formato ajuda a calcular a media. Para separar registros por um campo, pode-se usar `group-by`:

```clojure
(group-by :campo [{:campo "a"} {:campo "b"} {:campo "a"}])
```

## Erros e repeticao

`ex-info` permite gerar uma excecao com uma mensagem e dados adicionais. A interface captura erros e mostra uma linha iniciada por `ERRO:`.

```clojure
(try
  (throw (ex-info "campo inexistente" {:campo :campo}))
  (catch clojure.lang.ExceptionInfo erro
    [(ex-message erro) (ex-data erro)]))
;; => ["campo inexistente" {:campo :campo}]
```

`loop` e `recur` repetem uma operacao sem alterar variaveis: `recur` volta ao inicio com os novos valores. O terminal usa essa estrutura para ler comandos ate receber `EXIT` ou chegar ao fim da entrada. Leitura e impressao ficam no namespace `consultas.main`.

```clojure
(loop [total 0
       valores [1 2 3]]
  (if (empty? valores)
    total
    (recur (inc total) (rest valores))))
;; => 3
```

## Ordenacao e formatacao

`sort-by` ordena registros pela chave escolhida. Um comparador invertido coloca os valores em ordem decrescente. A ordenacao e estavel, entao itens empatados mantem a ordem de entrada.

Para mostrar um decimal com duas casas e ponto, o programa fixa a localidade:

```clojure
(String/format java.util.Locale/ROOT "%.2f" (to-array [8.6]))
;; => "8.60"
```

## deps.edn

O `deps.edn` informa que o codigo esta em `src` e define o alias `:run`, usado para iniciar `consultas.main`:

```clojure
{:paths ["src"]
 :aliases {:run {:main-opts ["-m" "consultas.main"]}}}
```

## Uso de IA

Utilizamos IA como apoio para documentar o projeto e esclarecer duvidas sobre as construcoes em Clojure.