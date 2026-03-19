import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import SchemaBrowser from './pages/SchemaBrowser';
import InstanceList from './pages/InstanceList';
import InstanceDetail from './pages/InstanceDetail';
import LinkList from './pages/LinkList';
import SchemaGraphView from './pages/SchemaGraphView';
import DataSourceManagement from './pages/DataSourceManagement';
import QueryBuilder from './pages/QueryBuilder';
import NaturalLanguageQuery from './pages/NaturalLanguageQuery';
import RulesView from './pages/RulesView';
import ReasoningView from './pages/ReasoningView';
import AgentChat from './pages/AgentChat';
import DashboardPage from './pages/DashboardPage';

function App() {
  return (
    <BrowserRouter>
      <Layout>
        <Routes>
          <Route path="/" element={<Navigate to="/schema" replace />} />
          <Route path="/schema" element={<SchemaBrowser />} />
          <Route path="/instances/:objectType" element={<InstanceList />} />
          <Route path="/instances/:objectType/:id" element={<InstanceDetail />} />
          <Route path="/links/:linkType" element={<LinkList />} />
          <Route path="/schema-graph" element={<SchemaGraphView />} />
          <Route path="/data-sources" element={<DataSourceManagement />} />
          <Route path="/query" element={<QueryBuilder />} />
          <Route path="/natural-language-query" element={<NaturalLanguageQuery />} />
          <Route path="/rules" element={<RulesView />} />
          <Route path="/reasoning" element={<ReasoningView />} />
          <Route path="/agent" element={<AgentChat />} />
          <Route path="/dashboard" element={<DashboardPage />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  );
}

export default App;
