export function DemoGuide() {
  return (
    <section className="demo-guide">
      <div>
        <h2>TicketForge Demo</h2>
        <ol>
          <li>Reserve tickets</li>
          <li>Observe available to reserved</li>
          <li>Simulate payment</li>
          <li>Observe reserved to sold</li>
        </ol>
      </div>
      <div className="rule-box">
        <strong>Inventory rules</strong>
        <span>Reserve: available - quantity, reserved + quantity</span>
        <span>Cancel: reserved - quantity, available + quantity</span>
        <span>Pay: reserved - quantity, sold + quantity</span>
      </div>
    </section>
  );
}
