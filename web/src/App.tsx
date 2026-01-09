import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
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
import SqlParsePage from './pages/SqlParse/SqlParsePage';


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
          <Route path="/graph/:objectType/:instanceId" element={<GraphView />} />
          <Route path="/data-sources" element={<DataSourceManagement />} />
          <Route path="/query" element={<QueryBuilder />} />
          <Route path="/metrics" element={<MetricBrowser />} />
          <Route path="/metrics/builder" element={<MetricBuilder />} />
          <Route path="/metrics/:id" element={<MetricDetail />} />
          <Route path="/natural-language-query" element={<NaturalLanguageQuery />} />
          <Route path="/sql-parse" element={<SqlParsePage />} />

        </Routes>
      </Layout>
    </BrowserRouter>
  );
}

export default App;
