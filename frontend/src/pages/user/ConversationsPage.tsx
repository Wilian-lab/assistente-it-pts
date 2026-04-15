const MOCK_CONVERSATIONS = [
  { icon: '💬', text: 'Pergunta sobre procedimento operacional na IT RP-IT-BN396.', date: 'Hoje' },
  { icon: '📄', text: 'Consulta de item no PTS para produto Farelo.', date: 'Ontem' },
  { icon: '👁', text: 'Visualização da última IT treinada.', date: '08/04' },
]

export function ConversationsPage() {
  return (
    <section className="page-section">
      <div className="dashboard-section-title">Histórico de Conversas</div>
      <div className="dashboard-section-subtitle">Acompanhe as interações já realizadas no assistente.</div>

      <div className="streamlit-card panel-block">
        <div className="panel-title">Conversas recentes</div>
        <div className="activity-list">
          {MOCK_CONVERSATIONS.map((item) => (
            <div key={`${item.text}-${item.date}`} className="activity-item">
              <div className="activity-icon">{item.icon}</div>
              <div className="activity-content">{item.text}</div>
              <div className="activity-date">{item.date}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
