import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import SchemaBrowser from './pages/SchemaBrowser';
import InstanceList from './pages/InstanceList';
import InstanceDetail from './pages/InstanceDetail';
import LinkList from './pages/LinkList';
import SchemaGraphView from './pages/SchemaGraphView';
import GraphView from './pages/GraphView';
import DataSourceManagement from './pages/DataSourceManagement';
import QueryBuilder from './pages/QueryBuilder';
import MetricBrowser from './pages/MetricBrowser';
import MetricBuilder from './pages/MetricBuilder';
import MetricDetail from './pages/MetricDetail';
import NaturalLanguageQuery from './pages/NaturalLanguageQuery';
import DataComparison from './pages/DataComparison';
import OntologyBuilder from './pages/OntologyBuilder';

import RulesView from './pages/RulesView';
import FunctionsView from './pages/FunctionsView';
import ReasoningView from './pages/ReasoningView';
import AgentChat from './pages/AgentChat';
import CelPlayground from './pages/CelPlayground';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { pathname } = useLocation();
  const isAuth = () => localStorage.getItem('mypalantir_auth') === 'true';
  if (!isAuth()) {
    return <Navigate to="/login" state={{ from: { pathname } }} replace />;
  }
  return <>{children}</>;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/*"
          element={
            <ProtectedRoute>
              <Layout>
                <Routes>
                  <Route index element={<Navigate to="/schema" replace />} />
                  <Route path="schema" element={<SchemaBrowser />} />
                  <Route path="instances/:objectType" element={<InstanceList />} />
                  <Route path="instances/:objectType/:id" element={<InstanceDetail />} />
                  <Route path="links/:linkType" element={<LinkList />} />
                  <Route path="schema-graph" element={<SchemaGraphView />} />
                  <Route path="graph/:objectType/:instanceId" element={<GraphView />} />
                  <Route path="data-sources" element={<DataSourceManagement />} />
                  <Route path="query" element={<QueryBuilder />} />
                  <Route path="metrics" element={<MetricBrowser />} />
                  <Route path="metrics/builder" element={<MetricBuilder />} />
                  <Route path="metrics/:id" element={<MetricDetail />} />
                  <Route path="natural-language-query" element={<NaturalLanguageQuery />} />
                  <Route path="data-comparison" element={<DataComparison />} />
                  <Route path="ontology-builder" element={<OntologyBuilder />} />
                  <Route path="rules" element={<RulesView />} />
                  <Route path="functions" element={<FunctionsView />} />
                  <Route path="cel-playground" element={<CelPlayground />} />
                  <Route path="reasoning" element={<ReasoningView />} />
                  <Route path="agent" element={<AgentChat />} />
                </Routes>
              </Layout>
            </ProtectedRoute>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
