
# Equipe :D

Paula Martins, Eric Donato e Matheus Batista

# Query builder funcional em Clojure

## Como executar

Com Clojure CLI instalado, execute na raiz do projeto:

```powershell
clojure -M:run dados/filmes.csv
```

Digite `QUERY` seguido de uma consulta. `EXIT` encerra; fim da entrada tambem encerra.

## Forma da AST

Uma consulta e um mapa com `:estagios` e `:terminal`. Os estagios sao vetores; quando houver agregacao terminal, ela fica em `:terminal`. `selecionar` e um estagio final.

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

Os tipos de estagio sao `:onde`, `:ordenar-por`, `:limitar` e `:selecionar`. Terminais aceitos: `[:contar]`, `[:soma campo]`, `[:media campo]` e `[:agrupar-por campo agregacao]`.

## Material de apoio

O [anexo sobre Clojure](anexo-clojure.md) resume as construcoes usadas no projeto, com exemplos pequenos.

## Namespaces

- `consultas.main`: leitura do CSV, prompt interativo e impressao dos resultados.
- `consultas.sintaxe`: tokenizacao e analise recursiva de texto para AST.
- `consultas.compilador`: validacao da AST contra o esquema e compilacao de nos em funcoes.
- `consultas.core`: leitura de CSV em texto, query builder e funcao `executar` usada pelos testes.

## Uso de IA

Utilizamos IA como apoio para documentar o projeto e esclarecer duvidas sobre as construcoes em Clojure.