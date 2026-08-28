import React, { useState } from 'react';
import { ANDROID_FILES } from '../data/androidFiles';
import { AndroidSourceFile } from '../types';
import { FileCode, Copy, Check, Download, Folder, FileText, Code2 } from 'lucide-react';

export const CodeExplorer: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<AndroidSourceFile>(ANDROID_FILES[0]);
  const [copied, setCopied] = useState<boolean>(false);
  const [categoryFilter, setCategoryFilter] = useState<string>('all');

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const filteredFiles = ANDROID_FILES.filter(f => 
    categoryFilter === 'all' ? true : f.category === categoryFilter
  );

  return (
    <div className="space-y-4">
      {/* Category Pills & Info */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-4 bg-neutral-900 rounded-2xl border border-neutral-800">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-neutral-400 mr-1">Filter Source:</span>
          {['all', 'samsung', 'restriction', 'config'].map((cat) => (
            <button
              key={cat}
              onClick={() => setCategoryFilter(cat)}
              className={`px-3 py-1 text-xs font-semibold rounded-lg capitalize transition-colors ${
                categoryFilter === cat
                  ? 'bg-blue-600 text-white'
                  : 'bg-neutral-800 text-neutral-300 hover:bg-neutral-700'
              }`}
            >
              {cat === 'all' ? 'All Files' : cat}
            </button>
          ))}
        </div>

        <div className="text-xs text-neutral-400 font-mono">
          Ready for Android Studio Ladybug (2024.2+) • Android 16 (API 36)
        </div>
      </div>

      {/* Code Viewer Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* File Tree Sidebar (4 Cols) */}
        <div className="lg:col-span-4 p-3 bg-neutral-900 rounded-2xl border border-neutral-800 space-y-2 h-[560px] overflow-y-auto">
          <div className="text-xs font-bold text-neutral-400 px-2 py-1 uppercase tracking-wider">
            Android Project Files
          </div>
          <div className="space-y-1">
            {filteredFiles.map((file) => {
              const isSelected = selectedFile.path === file.path;
              return (
                <button
                  key={file.path}
                  onClick={() => setSelectedFile(file)}
                  className={`w-full text-left p-2.5 rounded-xl text-xs font-mono transition-all flex items-start gap-2.5 ${
                    isSelected
                      ? 'bg-blue-950/80 border border-blue-800/80 text-blue-200 shadow-sm'
                      : 'hover:bg-neutral-800/70 text-neutral-300 border border-transparent'
                  }`}
                >
                  <FileCode className={`w-4 h-4 shrink-0 mt-0.5 ${isSelected ? 'text-blue-400' : 'text-neutral-500'}`} />
                  <div>
                    <div className="font-semibold">{file.name}</div>
                    <div className="text-[10px] text-neutral-500 font-sans truncate">{file.path}</div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Code Content Box (8 Cols) */}
        <div className="lg:col-span-8 flex flex-col bg-neutral-950 rounded-2xl border border-neutral-800 overflow-hidden h-[560px]">
          {/* Header */}
          <div className="p-3.5 bg-neutral-900 border-b border-neutral-800 flex items-center justify-between">
            <div className="space-y-0.5">
              <div className="flex items-center gap-2">
                <Code2 className="w-4 h-4 text-blue-400" />
                <span className="text-xs font-mono font-bold text-white">{selectedFile.path}</span>
              </div>
              <p className="text-[11px] text-neutral-400 font-sans">{selectedFile.description}</p>
            </div>

            <button
              onClick={handleCopy}
              className="px-3 py-1.5 rounded-lg bg-neutral-800 hover:bg-neutral-700 active:scale-95 text-xs font-semibold text-neutral-200 flex items-center gap-1.5 transition-all"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? 'Copied' : 'Copy Code'}
            </button>
          </div>

          {/* Code Textarea / Viewer */}
          <div className="flex-1 overflow-auto p-4 bg-neutral-950 font-mono text-xs text-neutral-200 leading-relaxed select-text">
            <pre className="whitespace-pre">
              <code>{selectedFile.content}</code>
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
};
