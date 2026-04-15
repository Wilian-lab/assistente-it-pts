import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { useChat } from '../../hooks/useChat'
import { usePtsData, usePtsItems, usePtsProducts } from '../../hooks/usePtsData'
import type { PtsItemData } from '../../services/pts/ptsService'
import { sanitizeText } from '../../utils/textSanitizer'
import { openAssistantPanel } from '../../utils/assistantPanel'

const DEFAULT_PRODUCTS = ['Todos', 'Farelo', 'Germe', 'Gluten']
const EMPTY_ITEM_OPTION = 'Selecione um item'

const FIELD_LABELS: Record<string, string> = {
  item: 'Item',
  classificacao: 'Classificacao',
  unidade: 'Unidade',
  limiteInf: 'Limite inferior',
  limiteSup: 'Limite superior',
  respColeta: 'Resp. coleta',
  respAnalise: 'Resp. analise',
  frequencia: 'Frequencia',
  metodoAnalise: 'Metodo analise',
  tag: 'TAG',
  tagAspen: 'TAG Aspen',
  fca: 'FCA',
  vaiNoApp: 'Vai no app',
  documentoReferencia: 'Documento referencia',
}

const PREFERRED_FIELDS: Array<keyof PtsItemData> = [
  'item',
  'classificacao',
  'unidade',
  'limiteInf',
  'limiteSup',
  'respColeta',
  'respAnalise',
  'frequencia',
  'metodoAnalise',
  'tag',
  'tagAspen',
  'fca',
  'vaiNoApp',
  'documentoReferencia',
]

function normalizeValue(value: string | undefined) {
  const cleaned = sanitizeText(value ?? '').trim()
  return cleaned.length > 0 ? cleaned : '-'
}

function sanitizeRow(row: PtsItemData): PtsItemData {
  return Object.fromEntries(
    Object.entries(row).map(([key, value]) => [key, typeof value === 'string' ? sanitizeText(value) : value]),
  ) as PtsItemData
}

function formatPtsItemResponse(product: string, item: string, rows: PtsItemData[]) {
  if (rows.length === 0) {
    return [
      `Nao encontrei o item ${sanitizeText(item)} na base filtrada.`,
      '',
      'Dica: selecione outro produto no filtro ou tente outro item.',
    ].join('\n')
  }

  const lines: string[] = [
    `CONSULTA DIRETA | ITEM ${sanitizeText(item)}`,
    `REGISTROS ENCONTRADOS: ${rows.length}`,
    '',
  ]

  rows.forEach((row, index) => {
    lines.push('------------------------------------------')
    lines.push(`REGISTRO ${index + 1}`)
    lines.push(`Variavel: ${normalizeValue(row.variavel)}`)
    lines.push(`Produto: ${normalizeValue(row.produto || product)}`)
    lines.push(`Etapa: ${normalizeValue(row.etapa)}`)
    lines.push('')
    lines.push('DADOS PRINCIPAIS')

    PREFERRED_FIELDS.forEach((field) => {
      const value = normalizeValue(row[field])
      if (value === '-') return
      lines.push(`- ${FIELD_LABELS[field]}: ${value}`)
    })

    const point = normalizeValue(row.pontoColeta)
    if (point !== '-') {
      lines.push('')
      lines.push('PONTO DE COLETA')
      lines.push(`- ${point}`)
    }

    const acaoAbaixo = normalizeValue(row.acaoAbaixo)
    const acaoAcima = normalizeValue(row.acaoAcima)
    if (acaoAbaixo !== '-' || acaoAcima !== '-') {
      lines.push('')
      lines.push('ACOES DE DESVIO')
      if (acaoAbaixo !== '-') {
        lines.push('- Abaixo do limite:')
        lines.push(acaoAbaixo)
      }
      if (acaoAcima !== '-') {
        lines.push('- Acima do limite:')
        lines.push(acaoAcima)
      }
    }

    lines.push('')
  })

  return lines.join('\n').trim()
}

export function DocumentationPage() {
  const navigate = useNavigate()
  const { addMessage, addActivity, incrementInteraction } = useChat()

  const [produto, setProduto] = useState('Todos')
  const [selectedItem, setSelectedItem] = useState(EMPTY_ITEM_OPTION)

  const productsQuery = usePtsProducts()
  const effectiveProduct = produto === 'Todos' ? (productsQuery.data?.[0] ?? '') : produto
  const itemsQuery = usePtsItems(effectiveProduct)
  const dataQuery = usePtsData(effectiveProduct, selectedItem !== EMPTY_ITEM_OPTION ? selectedItem : undefined)

  useEffect(() => {
    if (produto === 'Todos' && (productsQuery.data?.length ?? 0) > 0) {
      setProduto(productsQuery.data![0])
    }
  }, [produto, productsQuery.data])

  const productOptions = useMemo(() => {
    const fromApi = (productsQuery.data ?? []).map((value) => sanitizeText(value))
    return fromApi.length > 0 ? ['Todos', ...fromApi] : DEFAULT_PRODUCTS
  }, [productsQuery.data])

  const itemOptions = useMemo(
    () => [EMPTY_ITEM_OPTION, ...((itemsQuery.data ?? []).map((value) => sanitizeText(value)))],
    [itemsQuery.data],
  )

  const tableRows = useMemo(() => (dataQuery.data ?? []).map(sanitizeRow), [dataQuery.data])
  const hasPtsData = tableRows.length > 0 || (productsQuery.data?.length ?? 0) > 0

  useEffect(() => {
    if (selectedItem !== EMPTY_ITEM_OPTION && !itemOptions.includes(selectedItem)) {
      setSelectedItem(EMPTY_ITEM_OPTION)
    }
  }, [itemOptions, selectedItem])

  function handleBuscarItem() {
    if (selectedItem === EMPTY_ITEM_OPTION) return

    const targetProduct = produto === 'Todos' ? effectiveProduct : produto
    const timestamp = new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    const safeItem = sanitizeText(selectedItem)
    const safeProduct = sanitizeText(targetProduct)
    const responseText = formatPtsItemResponse(safeProduct, safeItem, tableRows)

    incrementInteraction()
    addActivity({
      icone: 'PTS',
      descricao: `Consulta PTS: item ${safeItem} (${safeProduct}).`,
      data: 'Hoje',
    })

    addMessage({
      role: 'user',
      content: `Buscar item ${safeItem} no PTS (${safeProduct})`,
      timestamp,
    })

    addMessage({
      role: 'assistant',
      content: responseText,
      timestamp,
      sourceType: 'pts_direct_lookup',
      metadata: {
        source: 'pts_system',
        lookupType: 'pts_item',
      },
    })

    openAssistantPanel()
    navigate('/')
  }

  return (
    <section className="page-section">
      <div className="dashboard-section-title">Documentacao PTS</div>
      <div className="dashboard-section-subtitle">
        Consulte a base do PTS por produto e item. Selecione um item e clique em "Buscar item" para consultar no
        assistente.
      </div>

      {!hasPtsData ? (
        <div className="panel-block streamlit-card">
          <div className="panel-title">Base PTS nao carregada</div>
          <p className="helper-text">
            Nenhum dado PTS foi encontrado. O administrador deve fazer o upload da planilha PTS_Agriproducts.xlsx em
            <strong> Arquivos</strong> para que os dados fiquem disponiveis.
          </p>
        </div>
      ) : null}

      <div className="panel-block streamlit-card compact-block">
        <div className="panel-title">Consulta PTS</div>
        <div className="documentation-toolbar">
          <label className="field-stack">
            <span>Filtrar por produto</span>
            <select
              value={produto}
              onChange={(event) => {
                setProduto(event.target.value)
                setSelectedItem(EMPTY_ITEM_OPTION)
              }}
            >
              {productOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>

          <label className="field-stack">
            <span>Buscar por item</span>
            <select value={selectedItem} onChange={(event) => setSelectedItem(event.target.value)}>
              {itemOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>

          <button
            type="button"
            className="outline-button documentation-search-button"
            onClick={handleBuscarItem}
            disabled={selectedItem === EMPTY_ITEM_OPTION || dataQuery.isLoading}
          >
            Buscar item
          </button>
        </div>
      </div>

      <div className="panel-block streamlit-card">
        <div className="panel-row-between" style={{ marginBottom: 12 }}>
          <div className="panel-title">Base PTS</div>
          <div className="helper-text">{tableRows.length} registros</div>
        </div>

        {dataQuery.isLoading ? <p>Carregando dados...</p> : null}

        <div className="table-shell">
          <table className="streamlit-table">
            <thead>
              <tr>
                <th>Produto</th>
                <th>Etapa</th>
                <th>Item</th>
                <th>Variavel</th>
                <th>Unidade</th>
                <th>Lim. Inf</th>
                <th>Lim. Sup</th>
                <th>Frequencia</th>
                <th>Resp. Coleta</th>
                <th>Ponto de Coleta</th>
              </tr>
            </thead>
            <tbody>
              {tableRows.map((row, index) => (
                <tr key={`${row.produto}-${row.item}-${row.variavel}-${index}`}>
                  <td>{normalizeValue(row.produto)}</td>
                  <td>{normalizeValue(row.etapa)}</td>
                  <td>{normalizeValue(row.item)}</td>
                  <td>{normalizeValue(row.variavel)}</td>
                  <td>{normalizeValue(row.unidade)}</td>
                  <td>{normalizeValue(row.limiteInf)}</td>
                  <td>{normalizeValue(row.limiteSup)}</td>
                  <td>{normalizeValue(row.frequencia)}</td>
                  <td>{normalizeValue(row.respColeta)}</td>
                  <td>{normalizeValue(row.pontoColeta)}</td>
                </tr>
              ))}
              {!dataQuery.isLoading && tableRows.length === 0 ? (
                <tr>
                  <td colSpan={10} style={{ textAlign: 'center', color: '#6b7fa3' }}>
                    {hasPtsData
                      ? 'Selecione um produto e/ou item para filtrar os dados.'
                      : 'Nenhum dado PTS disponivel. Faca upload da planilha em Arquivos.'}
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
