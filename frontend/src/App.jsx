import { NavLink, Route, Routes } from 'react-router-dom';
import WorkflowsPage from './pages/WorkflowsPage.jsx';
import WorkflowDetailPage from './pages/WorkflowDetailPage.jsx';
import ExecutionsPage from './pages/ExecutionsPage.jsx';
import ExecutionDetailPage from './pages/ExecutionDetailPage.jsx';

export default function App() {
  return (
    <>
      <nav className="top">
        <span className="brand">Workflow orchestrator</span>
        <NavLink to="/workflows">Workflows</NavLink>
        <NavLink to="/executions">Executions</NavLink>
      </nav>
      <div className="layout">
        <Routes>
          <Route path="/" element={<WorkflowsPage />} />
          <Route path="/workflows" element={<WorkflowsPage />} />
          <Route path="/workflows/:workflowId" element={<WorkflowDetailPage />} />
          <Route path="/executions" element={<ExecutionsPage />} />
          <Route path="/executions/:executionId" element={<ExecutionDetailPage />} />
        </Routes>
      </div>
    </>
  );
}
