import { NavLink, Route, Routes, useLocation } from 'react-router-dom';
import WorkflowsPage from './pages/WorkflowsPage.jsx';
import WorkflowDetailPage from './pages/WorkflowDetailPage.jsx';
import ExecutionsPage from './pages/ExecutionsPage.jsx';
import ExecutionDetailPage from './pages/ExecutionDetailPage.jsx';

export default function App() {
  const { pathname } = useLocation();
  const executionsSection =
    pathname === '/executions' || pathname.startsWith('/ui/executions');

  return (
    <>
      <nav className="top">
        <span className="brand">Workflow orchestrator</span>
        <NavLink to="/workflows">Workflows</NavLink>
        <NavLink
          to="/executions"
          className={() => (executionsSection ? 'active' : undefined)}
        >
          Executions
        </NavLink>
      </nav>
      <div className="layout">
        <Routes>
          <Route path="/" element={<WorkflowsPage />} />
          <Route path="/workflows" element={<WorkflowsPage />} />
          <Route path="/workflows/:workflowId" element={<WorkflowDetailPage />} />
          <Route path="/executions" element={<ExecutionsPage />} />
          <Route path="/ui/executions/:executionId" element={<ExecutionDetailPage />} />
        </Routes>
      </div>
    </>
  );
}
