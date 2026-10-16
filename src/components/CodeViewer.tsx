import { useState } from 'react';
import { Copy, Check, Download, FileCode, Folder, FolderOpen, Terminal } from 'lucide-react';
import { JAVA_PROJECT_FILES, JavaSourceFile } from '../data/javaSources';
import { downloadForgeStuckJar, downloadForgeStuckProjectZip } from '../utils/zipExporter';

export function CodeViewer() {
  const [selectedFile, setSelectedFile] = useState<JavaSourceFile>(JAVA_PROJECT_FILES[0]);
  const [copied, setCopied] = useState(false);
  const [downloadingZip, setDownloadingZip] = useState(false);
  const [downloadingJar, setDownloadingJar] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadZip = async () => {
    setDownloadingZip(true);
    try {
      await downloadForgeStuckProjectZip();
    } finally {
      setDownloadingZip(false);
    }
  };

  const handleDownloadJar = async () => {
    setDownloadingJar(true);
    try {
      await downloadForgeStuckJar();
    } finally {
      setDownloadingJar(false);
    }
  };

  const categories = [
    { key: 'all', label: 'All Files' },
    { key: 'core', label: 'Mod Entry & Transformer' },
    { key: 'shim', label: 'Runtime Shims' },
    { key: 'adapter', label: 'Target Mod Adapters' },
    { key: 'config', label: 'Gradle & Manifests' }
  ];

  const [activeCategory, setActiveCategory] = useState<string>('all');

  const filteredFiles = activeCategory === 'all'
    ? JAVA_PROJECT_FILES
    : JAVA_PROJECT_FILES.filter(f => f.category === activeCategory);

  return (
    <div className="space-y-4">
      {/* Top action bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl bg-slate-900/60 border border-slate-800">
        <div>
          <h2 className="text-base font-semibold font-display text-white flex items-center gap-2">
            <Terminal className="w-4 h-4 text-amber-400" />
            <span>Forge Stuck!! Java NeoForge 1.21.1 Source Code</span>
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Explore the complete Java bytecode transformer, NBT component shim, and target mod adapters.
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={handleDownloadJar}
            disabled={downloadingJar}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-amber-400 hover:bg-amber-300 text-slate-950 transition-colors shadow-sm disabled:opacity-50"
          >
            <Download className="w-3.5 h-3.5" />
            <span>{downloadingJar ? 'Packaging...' : 'Download Mod (.jar)'}</span>
          </button>
          <button
            onClick={handleDownloadZip}
            disabled={downloadingZip}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 transition-colors disabled:opacity-50"
          >
            <Download className="w-3.5 h-3.5" />
            <span>{downloadingZip ? 'Zipping...' : 'Full Project (.zip)'}</span>
          </button>
        </div>
      </div>

      {/* Main Code View Container */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* Left Column: File Tree */}
        <div className="lg:col-span-4 space-y-3">
          {/* Category Filter Pills */}
          <div className="flex flex-wrap gap-1 p-1 bg-slate-950/60 rounded-lg border border-slate-800/80">
            {categories.map((c) => (
              <button
                key={c.key}
                onClick={() => setActiveCategory(c.key)}
                className={`px-2.5 py-1 text-[11px] font-medium rounded-md transition-all ${
                  activeCategory === c.key
                    ? 'bg-slate-800 text-amber-300 shadow-sm'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                {c.label}
              </button>
            ))}
          </div>

          {/* Files List */}
          <div className="p-2 rounded-xl bg-slate-900/40 border border-slate-800 divide-y divide-slate-800/40 max-h-[560px] overflow-y-auto">
            {filteredFiles.map((file) => {
              const isSelected = selectedFile.path === file.path;
              return (
                <button
                  key={file.path}
                  onClick={() => setSelectedFile(file)}
                  className={`w-full text-left p-2.5 rounded-lg transition-all flex flex-col gap-1 ${
                    isSelected
                      ? 'bg-slate-800/90 text-white border border-amber-500/40'
                      : 'hover:bg-slate-800/40 text-slate-300'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-xs font-semibold truncate flex items-center gap-1.5">
                      <FileCode className={`w-3.5 h-3.5 ${isSelected ? 'text-amber-400' : 'text-slate-500'}`} />
                      {file.filename}
                    </span>
                    <span className="text-[10px] font-mono uppercase text-slate-500">
                      {file.category}
                    </span>
                  </div>
                  <span className="text-[11px] text-slate-400 line-clamp-1">
                    {file.description}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right Column: Code Editor View */}
        <div className="lg:col-span-8 p-4 rounded-xl bg-slate-900/40 border border-slate-800 flex flex-col justify-between">
          <div>
            {/* File info bar */}
            <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3">
              <div>
                <div className="font-mono text-xs text-amber-300 font-medium">
                  {selectedFile.path}
                </div>
                <div className="text-xs text-slate-400 mt-0.5">
                  {selectedFile.description}
                </div>
              </div>
              <button
                onClick={handleCopy}
                className="flex items-center gap-1.5 px-2.5 py-1 text-xs text-slate-300 hover:text-white bg-slate-800/80 hover:bg-slate-700/80 rounded-md border border-slate-700 transition-colors"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'Copied' : 'Copy Code'}</span>
              </button>
            </div>

            {/* Code Body */}
            <div className="relative rounded-lg bg-slate-950 border border-slate-800/90 overflow-hidden">
              <div className="p-4 overflow-x-auto max-h-[500px] text-xs font-mono text-slate-200 leading-relaxed">
                <pre>
                  <code>{selectedFile.code}</code>
                </pre>
              </div>
            </div>
          </div>

          <div className="mt-3 pt-3 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-500 font-mono">
            <span>Target: NeoForge 1.21.1 · Java 21</span>
            <span>Lines: {selectedFile.code.split('\n').length}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
