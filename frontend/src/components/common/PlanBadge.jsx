/** Plan label. Pass `null` while the plan is still loading: a neutral placeholder, never a guess. */
function PlanBadge({
  plan = "FREE"
}) {

  if (!plan) {
    return (
      <span className="plan-badge plan-loading" aria-busy="true" aria-label="Loading plan">
        …
      </span>
    );
  }

  return (
    <span
      className={`plan-badge plan-${plan.toLowerCase()}`}
    >
      {plan}
    </span>
  );
}


export default PlanBadge;
