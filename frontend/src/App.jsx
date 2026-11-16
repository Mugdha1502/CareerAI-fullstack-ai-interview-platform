import AppRoutes from "./routes/AppRoutes";
import SubscriptionProvider from "./context/SubscriptionProvider";
import UsageLimitModal from "./components/common/UsageLimitModal";


function App() {

  return (
    <SubscriptionProvider>
      <AppRoutes />
      {/* One plan-limit dialog for every module (opens on HTTP 429 PLAN_LIMIT_REACHED). */}
      <UsageLimitModal />
    </SubscriptionProvider>
  );
}


export default App;
