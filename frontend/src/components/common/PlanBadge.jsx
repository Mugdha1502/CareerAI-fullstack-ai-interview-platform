function PlanBadge({
  plan = "FREE"
}) {

  return (
    <span
      className={`plan-badge plan-${plan.toLowerCase()}`}
    >
      {plan}
    </span>
  );
}


export default PlanBadge;