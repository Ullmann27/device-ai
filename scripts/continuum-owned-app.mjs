#!/usr/bin/env node
// EXP-00/08/09: a fixed, owner-approved Android project recipe. No model-controlled source,
// arbitrary paths, shell commands, install requests, or edits to Device AI itself.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

export const RECIPE = Object.freeze({id:'m4-notes',packageName:'at.heinz.continuum.m4notes',versionCode:1,versionName:'0.1.0'});
const SHA = b => createHash('sha256').update(b).digest('hex');
const EXPECTED_CERT = /^[0-9a-f]{64}$/;
const PROJECT_DIR = 'continuum-projects';
const FILES = Object.freeze({
  'settings.gradle.kts': `pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "ContinuumNotesM4"
include(":app")
`,
  'build.gradle.kts': `plugins {
    id("com.android.application") version "8.10.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
}
`,
  'gradle.properties': 'org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\n',
  'app/build.gradle.kts': `plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "at.heinz.continuum.m4notes"
    compileSdk = 36
    defaultConfig {
        applicationId = "at.heinz.continuum.m4notes"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
dependencies { testImplementation("junit:junit:4.13.2") }
`,
  'app/src/main/AndroidManifest.xml': `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:allowBackup="false" android:label="Continuum Notizen" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
`,
  'app/src/main/java/at/heinz/continuum/m4notes/NotePolicy.kt': `package at.heinz.continuum.m4notes

internal object NotePolicy {
    const val MAX_LENGTH = 4000
    fun normalized(input: String): String = input.trim().take(MAX_LENGTH)
}
`,
  'app/src/main/java/at/heinz/continuum/m4notes/MainActivity.kt': `package at.heinz.continuum.m4notes

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Deterministic offline M4 app: two screens and private local persistence; no network. */
class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("continuum_m4_notes", MODE_PRIVATE) }
    private lateinit var body: LinearLayout
    private var draft: String? = null
    private var editor: EditText? = null

    private fun captureDraft() { editor?.let { draft = it.text.toString() } }

    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft()
        draft?.let { outState.putString("continuum_m4_draft", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draft = savedInstanceState?.getString("continuum_m4_draft")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val notes = Button(this).apply { text = "Notizen"; setOnClickListener { captureDraft(); showNotes() } }
        val about = Button(this).apply { text = "Über"; setOnClickListener { captureDraft(); showAbout() } }
        navigation.addView(notes, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        navigation.addView(about, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(navigation)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP }
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        showNotes()
    }

    private fun showNotes() {
        body.removeAllViews()
        val heading = TextView(this).apply { text = "Meine lokalen Notizen"; textSize = 23f }
        val editor = EditText(this).apply {
            setText(draft ?: prefs.getString("note", "") ?: "")
            hint = "Hier schreiben"
            minLines = 5
            maxLines = 12
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        this.editor = editor
        val result = TextView(this).apply { text = "Noch keine Änderungen gespeichert." }
        val save = Button(this).apply {
            text = "Lokal speichern"
            setOnClickListener {
                val normalized = NotePolicy.normalized(editor.text.toString())
                val persisted = prefs.edit().putString("note", normalized).commit()
                if (persisted) draft = normalized
                result.text = if (persisted) "Gespeichert (${'${'}normalized.length} Zeichen)." else "Speichern fehlgeschlagen."
            }
        }
        body.addView(heading)
        body.addView(editor)
        body.addView(save)
        body.addView(result)
    }

    private fun showAbout() {
        editor = null
        body.removeAllViews()
        body.addView(TextView(this).apply {
            text = "Continuum M4: Eigenständig erzeugte Android-Notiz-App. Zwei Ansichten, lokaler Speicher, keine Netzberechtigung."
            textSize = 18f
        })
    }
}
`,
  'app/src/test/java/at/heinz/continuum/m4notes/NotePolicyTest.kt': `package at.heinz.continuum.m4notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePolicyTest {
    @Test fun trimsWhitespace() { assertEquals("Hallo", NotePolicy.normalized("  Hallo \\n")) }
    @Test fun boundsNotes() { assertEquals(4000, NotePolicy.normalized("x".repeat(5000)).length) }
    @Test fun keepsEmptyNotes() { assertEquals("", NotePolicy.normalized("   ")) }
}
`,
  'README.md': `# Continuum M4 – eigene Android-Notiz-App

Dieses Projekt wurde durch das fest geprüfte Continuum-Rezept m4-notes erzeugt. Android-first, eigenständiges Paket, keine Netzberechtigung. Zwei Seiten (Notizen / Über) und private lokale Speicherung. Kein fremder Coding-Agent, keine automatisch erzeugten Shell-Befehle. Das Projekt ist eine reproduzierbare erste Vorlage, noch kein Beweis freier Programmsynthese.

Build nur über die explizite Termux-Operation, niemals durch automatische Installation.
`
});
const MANIFEST_NAME = 'continuum-source-manifest.json';
const manifestText = () => JSON.stringify({schema:'continuum-owned-project',version:1,recipe:RECIPE,files:Object.fromEntries(Object.entries(FILES).map(([name,value])=>[name,SHA(value)]))},null,2)+'\n';
export function projectPath(home=os.homedir()) {
    if (typeof home !== 'string' || !path.isAbsolute(home)) throw Error('INVALID_HOME');
    const stat = fs.lstatSync(home);
    if (!stat.isDirectory() || stat.isSymbolicLink()) throw Error('UNSAFE_HOME');
    return path.join(fs.realpathSync(home),PROJECT_DIR,RECIPE.id);
}
function ensureSafeParent(home) {
    const project = projectPath(home);
    const parent = path.dirname(project);
    if (!fs.existsSync(parent)) fs.mkdirSync(parent,{mode:0o700});
    const stat = fs.lstatSync(parent);
    if (!stat.isDirectory() || stat.isSymbolicLink()) throw Error('UNSAFE_PROJECT_PARENT');
    return project;
}
export function createOwnedApp({home=os.homedir()}={}) {
    const dest = ensureSafeParent(home);
    if (fs.existsSync(dest) || fs.existsSync(path.dirname(dest)) && fs.readdirSync(path.dirname(dest)).includes(RECIPE.id))
        return {status:'BLOCKED_PROJECT_EXISTS',recipe:RECIPE.id};
    // mkdir is exclusive; never overwrite or delete an existing owner's project.
    try { fs.mkdirSync(dest,{mode:0o700}); }
    catch (error) { if (error.code === 'EEXIST') return {status:'BLOCKED_PROJECT_EXISTS',recipe:RECIPE.id}; throw error; }
    try {
        for (const [name,value] of Object.entries({...FILES,[MANIFEST_NAME]:manifestText()})) {
            const full = path.join(dest,name);
            fs.mkdirSync(path.dirname(full),{recursive:true,mode:0o700});
            fs.writeFileSync(full,value,{flag:'wx',mode:0o600});
        }
    } catch (error) {
        // Do not remove/overwrite anything after a partial failure: the owner may inspect or recover it.
        return {status:'FAILED_PROJECT_CREATION',recipe:RECIPE.id,reason:error.code||'WRITE_FAILED'};
    }
    return {status:'PROJECT_CREATED_NOT_BUILT',recipe:RECIPE.id,packageName:RECIPE.packageName,
       project:dest,sourceSha256:SHA(manifestText()),files:Object.keys(FILES).length,apk:null,installed:false};
}
export function verifyOwnedApp({home=os.homedir()}={}) {
    const dest = projectPath(home);
    const parent = path.dirname(dest);
    if (fs.existsSync(parent) && (!fs.lstatSync(parent).isDirectory() || fs.lstatSync(parent).isSymbolicLink()))
        return {status:'BLOCKED_UNSAFE_PROJECT_PARENT'};
    if (!fs.existsSync(dest)) return {status:'BLOCKED_PROJECT_NOT_FOUND'};
    if (fs.lstatSync(dest).isSymbolicLink()||!fs.lstatSync(dest).isDirectory()) return {status:'BLOCKED_UNSAFE_PROJECT'};
    const expected = {...FILES,[MANIFEST_NAME]:manifestText()};
    for (const [name,body] of Object.entries(expected)) {
        const full = path.join(dest,name);
        try {
            let ancestor = dest;
            for (const segment of name.split('/').slice(0,-1)) {
                ancestor = path.join(ancestor,segment);
                const directory = fs.lstatSync(ancestor);
                if (!directory.isDirectory() || directory.isSymbolicLink())
                    return {status:'BLOCKED_UNSAFE_SOURCE_PATH',file:name};
            }
            const stat = fs.lstatSync(full);
            if (!stat.isFile()||stat.isSymbolicLink()||stat.size!==Buffer.byteLength(body)||SHA(fs.readFileSync(full))!==SHA(body))
                return {status:'BLOCKED_SOURCE_CHANGED',file:name};
        } catch { return {status:'BLOCKED_SOURCE_MISSING',file:name}; }
    }
    // Gradle loads buildSrc, source-set overlays and extra Kotlin/Java automatically.
    // Hashing only the expected files does not constrain those additional inputs.
    const allowedDirectories = new Set();
    for (const name of Object.keys(expected)) {
        const parts = name.split('/');
        for (let i=1;i<parts.length;i++) allowedDirectories.add(parts.slice(0,i).join('/'));
    }
    const outputs = new Set(['.gradle','.kotlin','build','app/build']);
    const pending = [''];
    while (pending.length) {
        const directory = pending.pop();
        for (const entry of fs.readdirSync(path.join(dest,directory),{withFileTypes:true})) {
            const name = directory ? directory+'/'+entry.name : entry.name;
            if (outputs.has(name)) continue; // roots checked separately before and after Gradle
            if (Object.hasOwn(expected,name)) continue;
            if (entry.isDirectory() && allowedDirectories.has(name)) pending.push(name);
            else return {status:'BLOCKED_UNEXPECTED_SOURCE',file:name};
        }
    }
    return {status:'SOURCE_VERIFIED',project:dest,recipe:RECIPE.id,packageName:RECIPE.packageName,
       sourceSha256:SHA(manifestText())};
}
/** The generated Gradle output is writable but must never escape via a symlink. */
function outputWithinProject(project) {
    for (const part of ['.gradle','.kotlin','build']) {
      try {
        const info = fs.lstatSync(path.join(project,part));
        if (!info.isDirectory() || info.isSymbolicLink()) return false;
      } catch (e) { if (e.code !== 'ENOENT') return false; }
    }
    let ancestor = project;
    for (const part of ['app','build','outputs','apk','debug']) {
      ancestor = path.join(ancestor,part);
      if (!fs.existsSync(ancestor)) {
        try { fs.lstatSync(ancestor); } catch (e) {
          if (e.code === 'ENOENT') continue;
          return false;
        }
      }
      try {
        const info = fs.lstatSync(ancestor);
        if (!info.isDirectory() || info.isSymbolicLink()) return false;
      } catch (e) { if (e.code !== 'ENOENT') return false; }
    }
    return true;
}
function executable(name,env) {
    for(const directory of (env.PATH||'').split(path.delimiter)) {
      if (!directory) continue;
      const candidate=path.join(directory,name);
      try {const st=fs.statSync(candidate);if(st.isFile()&&(st.mode&0o111))return candidate;}catch{}
    }
    return null;
}
function execute(file,args,{cwd,env,timeout}) {
    const r=spawnSync(file,args,{cwd,env,timeout,encoding:'utf8',shell:false,maxBuffer:1024*512});
    return {ok:r.status===0&&!r.error,code:r.status,output:(r.stdout||'')+'\n'+(r.stderr||'')};
}
/** Never build modified sources; perform real Gradle, APK identity and signature checks. */
export async function buildOwnedApp({home=os.homedir(),env=process.env,arch=process.arch,run=execute,inspect=null,lease=null}={}) {
    const source=verifyOwnedApp({home});
    if (source.status!=='SOURCE_VERIFIED') return source;
    if (!outputWithinProject(source.project)) return {status:'BLOCKED_UNSAFE_BUILD_OUTPUT'};
    const {doctor,resolveTermuxEnvironment} = await import('./continuum-mobile-builder.mjs');
    const resolvedEnv=resolveTermuxEnvironment(env,arch);
    const tools=inspect?await inspect():await doctor({env:resolvedEnv,arch});
    if(tools.status!=='READY_FOR_EXPERIMENTAL_BUILD')return {status:'BLOCKED_MISSING_TOOLCHAIN'};
    const gradle=tools.bin?.gradle, aapt2=tools.bin?.aapt2, signer=tools.bin?.apksigner;
    // The verified Termux doctor supplies native aapt2, not legacy aapt.
    // Reuse its exact executable, as the existing Continuum Android builder does.
    if(!gradle||!aapt2||!signer)
      return {status:'BLOCKED_MISSING_TOOLCHAIN'};
    const release=lease||((await import('../.github/continuum/shared-build-lease.mjs')).acquireContinuumBuildLease(source.project));
    try {
      if (!outputWithinProject(source.project)) return {status:'BLOCKED_UNSAFE_BUILD_OUTPUT'};
      const args=['--no-daemon','--max-workers=2','--offline','--console=plain',
        '-Pandroid.aapt2FromMavenOverride='+aapt2,
        '-Pkotlin.compiler.execution.strategy=in-process',
        ':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'];
      const gradleRun=await run(gradle,args,{cwd:source.project,env:resolvedEnv,timeout:35*60*1000});
      if(!gradleRun.ok)return {status:'FAILED_BUILD',exitCode:gradleRun.code};
      const integrity=verifyOwnedApp({home});
      if(integrity.status!=='SOURCE_VERIFIED')return {status:'BLOCKED_SOURCE_CHANGED_DURING_BUILD'};
      if (!outputWithinProject(source.project)) return {status:'BLOCKED_UNSAFE_BUILD_OUTPUT'};
      const apk=path.join(source.project,'app/build/outputs/apk/debug/app-debug.apk');
      if(!fs.existsSync(apk)||!fs.lstatSync(apk).isFile()||fs.lstatSync(apk).isSymbolicLink())
        return {status:'FAILED_NO_APK'};
      const homeRoot=fs.realpathSync(home);
      let state=homeRoot;
      for (const segment of ['.local','state','device-ai-continuum','owned-projects']) {
        state=path.join(state,segment);
        if (!fs.existsSync(state)) fs.mkdirSync(state,{mode:0o700});
        const directory=fs.lstatSync(state);
        if (!directory.isDirectory() || directory.isSymbolicLink())
          return {status:'BLOCKED_UNSAFE_STATE'};
      }
      // Exactly the bytes copied into this private stage must pass both independent
      // verifiers and the final digest. Never check a mutable Gradle output and then
      // copy potentially different bytes into the owner artifact.
      const {randomUUID}=await import('node:crypto');
      const staged=path.join(state,'.m4-apk-'+randomUUID()+'.tmp');
      try {
        try { fs.copyFileSync(apk,staged,fs.constants.COPYFILE_EXCL);fs.chmodSync(staged,0o600); }
        catch { return {status:'FAILED_ARTIFACT_COPY'}; }
        const stagedSize=fs.statSync(staged).size;
        const stagedDigest=SHA(fs.readFileSync(staged));
        const unchanged=()=>{
          try {
            const st=fs.lstatSync(staged);
            return st.isFile()&&!st.isSymbolicLink()&&st.size===stagedSize&&
              SHA(fs.readFileSync(staged))===stagedDigest;
          } catch { return false; }
        };
        const cert=await run(signer,['verify','--verbose','--print-certs',staged],
          {cwd:source.project,env:resolvedEnv,timeout:30000});
        const certMatch=/certificate SHA-256 digest:\s*([a-fA-F0-9]{64})/i.exec(cert.output);
        if(!cert.ok||!certMatch||!EXPECTED_CERT.test(certMatch[1].toLowerCase()))
          return {status:'FAILED_APK_SIGNATURE'};
        if(!unchanged())return {status:'FAILED_ARTIFACT_CHANGED'};
        const identity=await run(aapt2,['dump','badging',staged],
          {cwd:source.project,env:resolvedEnv,timeout:30000});
        if(!identity.ok || !identity.output.includes(
          "package: name='"+RECIPE.packageName+"' versionCode='1' versionName='0.1.0'"))
          return {status:'FAILED_APK_IDENTITY'};
        if(!unchanged())return {status:'FAILED_ARTIFACT_CHANGED'};
        if(verifyOwnedApp({home}).status!=='SOURCE_VERIFIED')
          return {status:'BLOCKED_SOURCE_CHANGED_DURING_BUILD'};
        if(!outputWithinProject(source.project))
          return {status:'BLOCKED_UNSAFE_BUILD_OUTPUT'};
        const digest=stagedDigest;
        const saved=path.join(state,'Continuum-M4-Notes-'+digest.slice(0,16)+'.apk');
        try{fs.copyFileSync(staged,saved,fs.constants.COPYFILE_EXCL);}
        catch(error){if(error.code!=='EEXIST')return {status:'FAILED_ARTIFACT_COPY'};}
        if(!fs.lstatSync(saved).isFile()||fs.lstatSync(saved).isSymbolicLink()||
           SHA(fs.readFileSync(saved))!==digest)return {status:'FAILED_ARTIFACT_COPY'};
        return {status:'EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED',
          packageName:RECIPE.packageName,versionCode:1,apkSha256:digest,
          signerSha256:certMatch[1].toLowerCase(),sourceSha256:source.sourceSha256,
          project:source.project,artifact:saved,installed:false,productionReady:false};
      } finally {
        try { fs.rmSync(staged,{force:true}); } catch { /* explicit artifact verification still mandatory */ }
      }

    } finally {release();}
}
async function main() {
  const [command,...args]=process.argv.slice(2);
  if (args.length || !['create','verify','build'].includes(command))throw Error('USAGE: create|verify|build (no extra arguments)');
  const report=command==='create'?createOwnedApp():command==='verify'?verifyOwnedApp():await buildOwnedApp();
  process.stdout.write(JSON.stringify(report)+'\n');
  if(!['PROJECT_CREATED_NOT_BUILT','SOURCE_VERIFIED','EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED'].includes(report.status))process.exitCode=2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))
    main().catch(error=>{process.stderr.write('Continuum M4: '+String(error.message).slice(0,240)+'\n');process.exitCode=2;});
