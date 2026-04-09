Quero que você atue como um arquiteto e desenvolvedor sênior de software, especialista em migração de front-end corporativo, React e integração com backend Java.

Contexto do sistema:
Hoje eu tenho um sistema chamado PTS Chatbot, com duas partes principais:

1. Backend em Java
   Esse backend contém as regras oficiais de negócio e os dados do sistema.
   O domínio do usuário possui, entre outros, os seguintes atributos:

- id
- email
- name
- password
- role (administrador ou usuário)
- treinamento IT
- data do treinamento IT
- próxima data de treinamento IT
- intervalo de dias para retreinamento
- cargo

As regras de negócio do sistema estão no Java e devem continuar sendo a fonte oficial da verdade.

2. Front-end atual em Python com Streamlit
   Esse front-end funciona como um assistente PTS.
   O usuário interage com um chatbot.
   Exemplo:

- o usuário seleciona uma IT
- o usuário pergunta algo como "resfriamento"
- o sistema busca essa informação dentro da IT selecionada
- o sistema retorna uma resposta orientando o que deve ser feito, com base no conteúdo da IT

Objetivo:
Quero migrar o front-end atual, que hoje está em Python/Streamlit, para React.
O backend continuará em Java.
O React deve reproduzir fielmente o comportamento funcional do assistente atual, mas sem carregar a regra de negócio.
Toda regra de negócio deve permanecer no backend Java.

Importante:

- O projeto em Streamlit continuará existindo temporariamente como ambiente de teste, validação e comparação funcional
- O projeto React será o front-end consolidado para deploy
- O React não deve inventar fluxos novos sem necessidade
- O React deve seguir explicitamente as mesmas regras já aplicadas no assistente atual e respeitar totalmente o backend Java
- Qualquer lógica crítica deve estar no backend, nunca no front-end
- O front-end React deve ser moderno, organizado, escalável e preparado para ambiente corporativo

O que eu quero que você faça:

1. Mapear a arquitetura atual do PTS Chatbot em Python/Streamlit
2. Identificar tudo que é:
   - interface
   - fluxo de navegação
   - comportamento do chatbot
   - regra de negócio
   - validação
   - consulta ao backend
3. Separar claramente o que deve:
   - permanecer no backend Java
   - ser implementado no front-end React
4. Propor a migração do Streamlit para React sem perder comportamento
5. Garantir aderência total às regras de negócio existentes
6. Estruturar o front-end React para produção
7. Considerar que o Streamlit será usado apenas como base de comparação e testes
8. Não quebrar a lógica do assistente existente

Regras obrigatórias:

- O backend Java é a fonte oficial das regras de negócio
- O React deve apenas representar dados, capturar interações e consumir APIs
- O React não deve decidir regra de permissão, treinamento, role, bloqueio ou lógica de consulta
- Toda regra crítica deve vir do backend Java
- O comportamento do assistente no React deve seguir explicitamente o comportamento validado no Streamlit
- O React deve ser preparado para manutenção futura, componentização, escalabilidade e deploy corporativo
- Sempre que houver dúvida entre “colocar lógica no front” ou “colocar no backend”, escolher backend
- A migração deve preservar o comportamento do assistente atual
- Não simplificar demais a solução
- Não propor um protótipo genérico; propor uma estrutura realista de produção

Quero que você me entregue a resposta organizada nas seguintes seções:

1. Resumo da arquitetura recomendada
   Explique de forma objetiva como deve ficar a arquitetura final com Java + React e o papel temporário do Streamlit.
2. Mapeamento do sistema atual
   Descreva o que normalmente pertence hoje ao Streamlit e o que deve ser preservado como comportamento.
3. Separação de responsabilidades
   Liste claramente:

- o que fica no Java
- o que fica no React
- o que pode continuar em Python apenas se for estritamente necessário

4. Estratégia de migração
   Explique passo a passo como migrar do Streamlit para React sem perder as regras aplicadas ao assistente.
5. Estrutura do front-end React
   Monte uma sugestão de estrutura de pastas, páginas, componentes, serviços, hooks, rotas e organização do projeto.
6. Fluxos principais do sistema
   Descreva os principais fluxos:

- login
- carregamento do usuário
- validação de permissões
- seleção de IT
- envio de pergunta no chatbot
- renderização da resposta
- tratamento de erro
- bloqueios por regra de negócio vindos do backend

7. Integração com backend Java
   Sugira endpoints esperados e como o React deve consumir cada um, sem mover regras de negócio para o front-end.
8. Cuidados técnicos importantes
   Aponte riscos comuns da migração, especialmente:

- duplicar regra no front-end
- inconsistência entre Streamlit e React
- lógica de permissão fora do backend
- diferenças de comportamento do chatbot
- falhas de estado e sessão

9. Plano de validação
   Crie um plano para comparar o comportamento do React com o Streamlit durante a transição, garantindo equivalência funcional.
10. Exemplo prático
    Monte um exemplo concreto de como ficaria:

- uma tela do chatbot em React
- a chamada da API para consulta
- o retorno esperado do backend
- como exibir a resposta preservando as regras do assistente

Diretriz final:
Sua resposta deve ser técnica, detalhada, prática e orientada a implementação real.
Não quero uma resposta genérica.
Quero uma proposta pensada para migração real de um sistema corporativo.
Considere explicitamente que o Streamlit será mantido para testes e que o React será o front-end definitivo.
Respeite integralmente o fato de que a regra de negócio do assistente já existe e precisa ser seguida sem desvios.

Restrições adicionais:

- Não crie nova regra de negócio sem necessidade explícita
- Não proponha mover inteligência do backend Java para o React
- Não trate o Streamlit como produto final
- Não responda em alto nível apenas conceitual
- Não omita estrutura técnica
- Não abstraia demais
- Sempre preserve compatibilidade funcional com o assistente atual
- Sempre considerar o backend Java como autoridade máxima

Agora, com base na arquitetura proposta, gere a estrutura inicial do front-end em React para o PTS Chatbot.

Requisitos:

- usar React com organização de projeto corporativa
- separar pages, components, services, hooks, routes e contexts
- criar uma base preparada para integração com backend Java
- não colocar regra de negócio crítica no front-end
- considerar que permissões e validações vêm do backend
- montar a tela principal do chatbot
- montar um serviço de API para consultar o backend
- prever autenticação e proteção de rotas
- usar nomes de arquivos, pastas e componentes claros
- comentar o código quando necessário
- deixar evidente onde o Streamlit serviu apenas como referência funcional

Entregue:

- árvore de pastas
- arquivos principais
- código inicial dos componentes
- exemplo de chamada da API
- exemplo de renderização da resposta do assistente
