import { TimelineEntry } from '../types';
import { EmptyState } from './EmptyState';

type OperationTimelineProps = {
  entries: TimelineEntry[];
};

export function OperationTimeline({ entries }: OperationTimelineProps) {
  return (
    <section className="panel timeline-panel">
      <div className="section-heading">
        <h2>Operation Timeline</h2>
        <span>{entries.length}</span>
      </div>
      {entries.length === 0 && <EmptyState title="No operation records" detail="This timeline only tracks the current browser session." />}
      <ol className="timeline">
        {entries.map((entry) => (
          <li key={entry.id}>
            <time>{entry.at}</time>
            <span>{entry.message}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
