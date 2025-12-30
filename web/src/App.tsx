import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import SchemaBrowser from './pages/SchemaBrowser';
import InstanceList from './pages/InstanceList';
import InstanceDetail from './pages/InstanceDetail';
import LinkList from './pages/LinkList';
import GraphView from './pages/GraphView';
import SchemaGraphView from './pages/SchemaGraphView';
import DataMapping from './pages/DataMapping';

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
          <Route path="/data-mapping/:objectType" element={<DataMapping />} />
          <Route path="/graph" element={<GraphView />} />
          <Route path="/graph/:objectType/:instanceId" element={<GraphView />} />
          <Route path="/schema-graph" element={<SchemaGraphView />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  );
}

export default App;
