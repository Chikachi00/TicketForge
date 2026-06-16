export function HowItWorks() {
  return (
    <section className="how-layout">
      <article className="panel">
        <p className="eyebrow">Architecture</p>
        <h2>Client to PostgreSQL transaction</h2>
        <div className="flow-row">
          <span>Client</span>
          <span>Spring Boot API</span>
          <span>PostgreSQL transaction</span>
          <span>Atomic conditional inventory update</span>
        </div>
      </article>
      <FlowCard
        title="Reservation"
        steps={['Request', 'Lock/check user idempotency', 'Atomic inventory update', 'Create PENDING_PAYMENT order']}
      />
      <FlowCard
        title="Payment"
        steps={['HMAC callback', 'Lock payment', 'Lock order', 'Reserved to sold', 'Order PAID']}
      />
      <FlowCard
        title="Expiration / Cancellation"
        steps={['Lock order', 'PENDING_PAYMENT to CANCELLED', 'Reserved to available']}
      />
      <article className="panel">
        <p className="eyebrow">Correctness run</p>
        <h2>GitHub Actions small-scale validation</h2>
        <div className="correctness-grid">
          <span>50 order attempts</span>
          <span>20 tickets</span>
          <span>20 successful reservations</span>
          <span>30 expected OUT_OF_STOCK</span>
          <span>0 oversell</span>
          <span>0 inventory inconsistency</span>
        </div>
        <p className="state">This is a small GitHub Actions correctness run, not a production capacity benchmark.</p>
      </article>
    </section>
  );
}

function FlowCard({ title, steps }: { title: string; steps: string[] }) {
  return (
    <article className="panel flow-card">
      <h2>{title}</h2>
      <ol>
        {steps.map((step) => (
          <li key={step}>{step}</li>
        ))}
      </ol>
    </article>
  );
}
