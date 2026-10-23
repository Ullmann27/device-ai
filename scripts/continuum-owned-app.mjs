#!/usr/bin/env node
// EXP-00/08/09: a fixed, owner-approved Android project recipe. No model-controlled source,
// arbitrary paths, shell commands, install requests, or edits to Device AI itself.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash,randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {getProjectPlan, projectPlanSha256} from './continuum-project-plan.mjs';
import {counterFiles} from './continuum-counter-recipe.mjs';
import {runOwnedCommand} from './continuum-owned-process.mjs';
import {readOwnedFile,readOwnedBuildEvidence,OWNED_RECEIPT_SCHEMA} from '../.github/continuum/owned-project-evidence.mjs';
import {verifyOwnedProjectReceipt} from '../.github/continuum/ccc1-verifier.mjs';

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
const recipeFor = plan => ({id:plan.id,packageName:plan.app.packageName,versionCode:plan.app.versionCode,versionName:plan.app.versionName});
function sourceFiles(plan) {
    const files={...FILES};
    if(plan.id==='m4-counter') {
      for(const name of Object.keys(files)) if(name.endsWith('.kt')&&!name.endsWith('.gradle.kts')) delete files[name];
      Object.assign(files,counterFiles());
      files['settings.gradle.kts']=files['settings.gradle.kts'].replace('ContinuumNotesM4','ContinuumCounterM4');
      files['README.md']='# Continuum M4 – lokaler Zähler\n\nZwei Ansichten; privater lokaler Zähler 0..9999; keine Berechtigungen. Installation nur nach Besitzerprüfung.\n';
      for(const name of ['app/build.gradle.kts','app/src/main/AndroidManifest.xml']) {
        files[name]=files[name].replaceAll(RECIPE.packageName,plan.app.packageName)
          .replaceAll('Continuum Notizen','Continuum Zähler');
      }
    }
    files['continuum-project-plan.json']=JSON.stringify(plan,null,2)+'\n';
    return files;
}
const manifestText = plan => JSON.stringify({schema:'continuum-owned-project',version:2,recipe:recipeFor(plan),
    planSha256:projectPlanSha256(plan),
    files:Object.fromEntries(Object.entries(sourceFiles(plan)).map(([name,value])=>[name,SHA(value)]))},null,2)+'\n';
export function projectPath(home=os.homedir(),recipeId=RECIPE.id) {
    const plan=getProjectPlan(recipeId);
    if (typeof home !== 'string' || !path.isAbsolute(home)) throw Error('INVALID_HOME');
    const stat = fs.lstatSync(home);
    if (!stat.isDirectory() || stat.isSymbolicLink()) throw Error('UNSAFE_HOME');
    return path.join(fs.realpathSync(home),PROJECT_DIR,plan.id);
}
function ensureSafeParent(home,recipeId) {
    const project = projectPath(home,recipeId);
    const parent = path.dirname(project);
    if (!fs.existsSync(parent)) fs.mkdirSync(parent,{mode:0o700});
    const stat = fs.lstatSync(parent);
    if (!stat.isDirectory() || stat.isSymbolicLink()) throw Error('UNSAFE_PROJECT_PARENT');
    return project;
}
export function createOwnedApp({home=os.homedir(),recipeId=RECIPE.id}={}) {
    const plan=getProjectPlan(recipeId),recipe=recipeFor(plan);
    const dest = ensureSafeParent(home,recipeId);
    if (fs.existsSync(dest) || fs.existsSync(path.dirname(dest)) && fs.readdirSync(path.dirname(dest)).includes(recipe.id))
        return {status:'BLOCKED_PROJECT_EXISTS',recipe:recipe.id};
    // mkdir is exclusive; never overwrite or delete an existing owner's project.
    try { fs.mkdirSync(dest,{mode:0o700}); }
    catch (error) { if (error.code === 'EEXIST') return {status:'BLOCKED_PROJECT_EXISTS',recipe:recipe.id}; throw error; }
    try {
        for (const [name,value] of Object.entries({...sourceFiles(plan),[MANIFEST_NAME]:manifestText(plan)})) {
            const full = path.join(dest,name);
            fs.mkdirSync(path.dirname(full),{recursive:true,mode:0o700});
            fs.writeFileSync(full,value,{flag:'wx',mode:0o600});
        }
    } catch (error) {
        // Do not remove/overwrite anything after a partial failure: the owner may inspect or recover it.
        return {status:'FAILED_PROJECT_CREATION',recipe:recipe.id,reason:error.code||'WRITE_FAILED'};
    }
    return {status:'PROJECT_CREATED_NOT_BUILT',recipe:recipe.id,packageName:recipe.packageName,
       project:dest,sourceSha256:SHA(manifestText(plan)),planSha256:projectPlanSha256(plan),
       files:Object.keys(sourceFiles(plan)).length,apk:null,installed:false};
}
export function verifyOwnedApp({home=os.homedir(),recipeId=RECIPE.id}={}) {
    const plan=getProjectPlan(recipeId),recipe=recipeFor(plan);
    const dest = projectPath(home,recipeId);
    const parent = path.dirname(dest);
    if (fs.existsSync(parent) && (!fs.lstatSync(parent).isDirectory() || fs.lstatSync(parent).isSymbolicLink()))
        return {status:'BLOCKED_UNSAFE_PROJECT_PARENT'};
    if (!fs.existsSync(dest)) return {status:'BLOCKED_PROJECT_NOT_FOUND'};
    if (fs.lstatSync(dest).isSymbolicLink()||!fs.lstatSync(dest).isDirectory()) return {status:'BLOCKED_UNSAFE_PROJECT'};
    const expected = {...sourceFiles(plan),[MANIFEST_NAME]:manifestText(plan)};
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
    return {status:'SOURCE_VERIFIED',project:dest,recipe:recipe.id,packageName:recipe.packageName,
       sourceSha256:SHA(manifestText(plan)),planSha256:projectPlanSha256(plan)};
}
/** The generated Gradle output is writable but must never escape via a symlink. */
function outputWithinProject(project) {
    // Check all existing writable outputs/caches, including lint and unit-test paths.
    // A link below app/build/intermediates can be a write escape just like an APK link.
    let entries=0;
    const check=(file,depth=0)=>{
      if(depth>32||++entries>50000)return false;
      let st;
      try {st=fs.lstatSync(file);}catch(e){return e.code==='ENOENT';}
      if(st.isSymbolicLink())return false;
      if(st.isFile())return depth>0&&st.nlink===1;
      if(!st.isDirectory())return false;
      const dir=fs.opendirSync(file);
      try {
        let item;
        while((item=dir.readSync()))if(!check(path.join(file,item.name),depth+1))return false;
        return true;
      } finally {dir.closeSync();}
    };
    for(const root of ['.gradle','.kotlin','build','app/build'])
      if(!check(path.join(project,root)))return false;
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
function ownedState(home,create=true) {
    let state=fs.realpathSync(home);
    for(const part of ['.local','state','device-ai-continuum','owned-projects']) {
      state=path.join(state,part);
      try {
        const st=fs.lstatSync(state);
        if(!st.isDirectory()||st.isSymbolicLink())throw Error('UNSAFE_STATE');
      } catch(error) {
        if(error.code!=='ENOENT')throw error;
        if(!create)return null;
        fs.mkdirSync(state,{mode:0o700});
      }
    }
    return state;
}
function persistProjectReceipt(state,recipeId,receipt) {
    const name='latest-'+recipeId+'.json',target=path.join(state,name);
    try {
      const old=JSON.parse(readOwnedFile(state,name,256*1024).bytes);
      if(old.schema!==OWNED_RECEIPT_SCHEMA||old.recipe!==recipeId)throw Error('UNSAFE_STATE');
    } catch(error) {if(error.code!=='ENOENT')throw error;}
    const tmp=path.join(state,'.receipt-'+randomUUID()+'.tmp');
    try {
      fs.writeFileSync(tmp,JSON.stringify(receipt,null,2)+'\n',{flag:'wx',mode:0o600});
      fs.renameSync(tmp,target);
    } finally {fs.rmSync(tmp,{force:true});}
    return receipt;
}
/** Read-only recovery: a historical report must still match current source, proof files and APK bytes. */
export function readOwnedAppStatus({home=os.homedir(),recipeId=RECIPE.id}={}) {
    const source=verifyOwnedApp({home,recipeId});
    if(source.status!=='SOURCE_VERIFIED')return source;
    try {
      const state=ownedState(home,false);
      if(!state||!fs.existsSync(path.join(state,'latest-'+recipeId+'.json')))
        return {status:'NO_PROJECT_BUILD_REPORT',recipe:recipeId};
      const r=JSON.parse(readOwnedFile(state,'latest-'+recipeId+'.json',256*1024).bytes);
      if(r.schema!==OWNED_RECEIPT_SCHEMA||r.version!==1||r.recipe!==recipeId)throw Error('INVALID_RECEIPT');
      const check=verifyOwnedProjectReceipt(r,{plan:getProjectPlan(recipeId),sourceSha256:source.sourceSha256,
        project:source.project,artifactRoot:state});
      return {status:'PROJECT_BUILD_STATUS',recipe:recipeId,packageName:source.packageName,
        sourceSha256:source.sourceSha256,planSha256:source.planSha256,buildStatus:r.status,
        toolchainProfile:r.toolchainProfile,verification:check.verdict,
        ...(check.verdict==='CONFIRMED'?{apkSha256:r.apkSha256,signerSha256:r.signerSha256,
          versionCode:r.versionCode,artifact:r.artifact}:{}),installed:false,deviceVerified:false};
    } catch {return {status:'BLOCKED_INVALID_BUILD_REPORT',recipe:recipeId};}
}
/** Never build modified sources; perform real Gradle, APK identity and signature checks. */
export async function buildOwnedApp({home=os.homedir(),recipeId=RECIPE.id,toolchainProfile='termux-arm64',
    env=process.env,arch=process.arch,run=runOwnedCommand,inspect=null,lease=null,signal=null}={}) {
    const plan=getProjectPlan(recipeId),recipe=recipeFor(plan);
    if(signal?.aborted)return {status:'BUILD_CANCELLED'};
    if(!['termux-arm64','host-sdk'].includes(toolchainProfile))return {status:'BLOCKED_MISSING_TOOLCHAIN'};
    const source=verifyOwnedApp({home,recipeId});
    if (source.status!=='SOURCE_VERIFIED') return source;
    if (!outputWithinProject(source.project)) return {status:'BLOCKED_UNSAFE_BUILD_OUTPUT'};
    const {doctor,resolveTermuxEnvironment} = await import('./continuum-mobile-builder.mjs');
    const resolvedEnv=resolveTermuxEnvironment(env,arch);
    const tools=inspect?await inspect():await doctor({env:resolvedEnv,arch,profile:toolchainProfile,
      run:(file,args,options)=>run(file,args,{...options,signal})});
    if(signal?.aborted)return {status:'BUILD_CANCELLED'};
    if(tools.status!=='READY_FOR_EXPERIMENTAL_BUILD'||
      tools.profile!==undefined&&tools.profile!==toolchainProfile)return {status:'BLOCKED_MISSING_TOOLCHAIN'};
    const gradle=tools.bin?.gradle, aapt2=tools.bin?.aapt2, signer=tools.bin?.apksigner;
    // The verified Termux doctor supplies native aapt2, not legacy aapt.
    // Reuse its exact executable, as the existing Continuum Android builder does.
    if(!gradle||!aapt2||!signer)
      return {status:'BLOCKED_MISSING_TOOLCHAIN'};
    let state;
    try {state=ownedState(home);}catch {return {status:'BLOCKED_UNSAFE_STATE'};}
    let release,releaseAllowed=true;
    try {release=lease||((await import('../.github/continuum/shared-build-lease.mjs')).acquireContinuumBuildLease(source.project));}
    catch {return {status:'BLOCKED_BUILD_BUSY'};}
    const startedAt=Date.now(),job=randomUUID();
    const base={schema:OWNED_RECEIPT_SCHEMA,version:1,job,recipe:recipeId,startedAt,toolchainProfile,
      planSha256:source.planSha256,sourceSha256:source.sourceSha256,project:source.project,
      packageName:recipe.packageName,versionCode:recipe.versionCode,
      installed:false,deviceVerified:false,productionReady:false};
    const finish=report=>{
      try {return persistProjectReceipt(state,recipeId,{...base,...report,finishedAt:Date.now()});}
      catch {return {...base,status:'FAILED_BUILD_STATE',finishedAt:Date.now()};}
    };
    try {
      if(signal?.aborted)return finish({status:'BUILD_CANCELLED'});
      if (!outputWithinProject(source.project)) return finish({status:'BLOCKED_UNSAFE_BUILD_OUTPUT'});
      const runName='run-'+job,runDir=path.join(state,runName);
      fs.mkdirSync(runDir,{mode:0o700});
      // Preserve old output as audit data, then require reports/APK at newly produced paths.
      // --rerun-tasks prevents cache/up-to-date results from being advertised as new executions.
      const oldPaths=['app/build/test-results/testDebugUnitTest/TEST-'+plan.tests.unitClass+'.xml',
        'app/build/reports/lint-results-debug.xml',plan.build.artifact];
      try {
        for(let i=0;i<oldPaths.length;i++) {
          const file=path.join(source.project,oldPaths[i]);
          try {fs.lstatSync(file);}catch(e){if(e.code==='ENOENT')continue;throw e;}
          readOwnedFile(source.project,oldPaths[i],i===2?plan.limits.maxApkBytes:2*1024*1024);
          fs.renameSync(file,path.join(runDir,'previous-'+i));
        }
      } catch {return finish({status:'BLOCKED_UNSAFE_BUILD_OUTPUT'});}
      persistProjectReceipt(state,recipeId,{...base,status:'BUILD_RUNNING'});
      const args=['--no-daemon','--max-workers=2','--offline','--console=plain','--rerun-tasks',
        '-Pandroid.aapt2FromMavenOverride='+aapt2,
        '-Pkotlin.compiler.execution.strategy=in-process',
        ':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'];
      const gradleRun=await run(gradle,args,{cwd:source.project,env:resolvedEnv,timeout:plan.limits.maxBuildMillis,signal});
      // Retain before any fallible I/O, including ENOSPC while writing this log.
      if(gradleRun.terminationConfirmed===false)releaseAllowed=false;
      const log=Buffer.from(gradleRun.output||'');
      fs.writeFileSync(path.join(runDir,'gradle.log'),log,{flag:'wx',mode:0o600});
      if(gradleRun.terminationConfirmed===false) {
        return finish({status:'BUILD_TERMINATION_UNCONFIRMED'});
      }
      if(signal?.aborted||gradleRun.cancelled)return finish({status:'BUILD_CANCELLED'});
      if(!gradleRun.ok)return finish({status:gradleRun.timedOut?'BUILD_TIMED_OUT':'FAILED_BUILD',exitCode:gradleRun.code});
      const integrity=verifyOwnedApp({home,recipeId});
      if(integrity.status!=='SOURCE_VERIFIED')return finish({status:'BLOCKED_SOURCE_CHANGED_DURING_BUILD'});
      if (!outputWithinProject(source.project)) return finish({status:'BLOCKED_UNSAFE_BUILD_OUTPUT'});
      const apk=path.join(source.project,'app/build/outputs/apk/debug/app-debug.apk');
      if(!fs.existsSync(apk)||!fs.lstatSync(apk).isFile()||fs.lstatSync(apk).isSymbolicLink())
        return finish({status:'FAILED_NO_APK'});
      let evidence;
      try {
        const reports=readOwnedBuildEvidence(source.project,plan,startedAt);
        evidence={log:{file:runName+'/gradle.log',sha256:SHA(log)}};
        for(const kind of ['unit','lint']) {
          const proof=reports[kind],bytes=readOwnedFile(source.project,proof.file).bytes;
          if(SHA(bytes)!==proof.sha256)throw Error('REPORT_CHANGED');
          const file=runName+'/'+kind+'.xml';
          fs.writeFileSync(path.join(state,file),bytes,{flag:'wx',mode:0o600});
          evidence[kind]={...proof,file};
        }
      } catch {return finish({status:'FAILED_BUILD_EVIDENCE'});}
      // Exactly the bytes copied into this private stage must pass both independent
      // verifiers and the final digest. Never check a mutable Gradle output and then
      // copy potentially different bytes into the owner artifact.
      const staged=path.join(state,'.m4-apk-'+randomUUID()+'.tmp');
      try {
        try {
          const bytes=readOwnedFile(source.project,plan.build.artifact,plan.limits.maxApkBytes).bytes;
          fs.writeFileSync(staged,bytes,{flag:'wx',mode:0o600});
        } catch { return finish({status:'FAILED_ARTIFACT_COPY'}); }
        const stagedSize=fs.statSync(staged).size;
        const stagedDigest=SHA(fs.readFileSync(staged));
        const unchanged=()=>{
          try {
            const st=fs.lstatSync(staged);
            return st.isFile()&&!st.isSymbolicLink()&&st.size===stagedSize&&
              SHA(readOwnedFile(state,path.basename(staged),plan.limits.maxApkBytes).bytes)===stagedDigest;
          } catch { return false; }
        };
        const cert=await run(signer,['verify','--verbose','--print-certs',staged],
          {cwd:source.project,env:resolvedEnv,timeout:30000,signal});
        if(cert.terminationConfirmed===false) {
          releaseAllowed=false;return finish({status:'BUILD_TERMINATION_UNCONFIRMED'});
        }
        if(signal?.aborted||cert.cancelled)return finish({status:'BUILD_CANCELLED'});
        const certMatch=/certificate SHA-256 digest:\s*([a-fA-F0-9]{64})/i.exec(cert.output);
        if(!cert.ok||!certMatch||!EXPECTED_CERT.test(certMatch[1].toLowerCase()))
          return finish({status:'FAILED_APK_SIGNATURE'});
        if(!unchanged())return finish({status:'FAILED_ARTIFACT_CHANGED'});
        const identity=await run(aapt2,['dump','badging',staged],
          {cwd:source.project,env:resolvedEnv,timeout:30000,signal});
        if(identity.terminationConfirmed===false) {
          releaseAllowed=false;return finish({status:'BUILD_TERMINATION_UNCONFIRMED'});
        }
        if(signal?.aborted||identity.cancelled)return finish({status:'BUILD_CANCELLED'});
        if(!identity.ok || !identity.output.includes(
          "package: name='"+recipe.packageName+"' versionCode='1' versionName='0.1.0'"))
          return finish({status:'FAILED_APK_IDENTITY'});
        if(!unchanged())return finish({status:'FAILED_ARTIFACT_CHANGED'});
        if(verifyOwnedApp({home,recipeId}).status!=='SOURCE_VERIFIED')
          return finish({status:'BLOCKED_SOURCE_CHANGED_DURING_BUILD'});
        if(!outputWithinProject(source.project))
          return finish({status:'BLOCKED_UNSAFE_BUILD_OUTPUT'});
        const digest=stagedDigest;
        const saved=path.join(state,(recipeId==='m4-notes'?'Continuum-M4-Notes-':'Continuum-M4-Counter-')+digest.slice(0,16)+'.apk');
        try{fs.copyFileSync(staged,saved,fs.constants.COPYFILE_EXCL);}
        catch(error){if(error.code!=='EEXIST')return finish({status:'FAILED_ARTIFACT_COPY'});}
        if(!fs.lstatSync(saved).isFile()||fs.lstatSync(saved).isSymbolicLink()||
           SHA(readOwnedFile(state,path.basename(saved),plan.limits.maxApkBytes).bytes)!==digest)
             return finish({status:'FAILED_ARTIFACT_COPY'});
        for(const [kind,proof] of [['signature',cert],['identity',identity]]) {
          const bytes=Buffer.from(proof.output),file=runName+'/'+kind+'.txt';
          fs.writeFileSync(path.join(state,file),bytes,{flag:'wx',mode:0o600});
          evidence[kind]={file,sha256:SHA(bytes),apkSha256:digest,exitCode:proof.code};
        }
        const result={...base,status:'EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED',
          packageName:recipe.packageName,versionCode:1,apkSha256:digest,recipe:recipeId,
          signerSha256:certMatch[1].toLowerCase(),sourceSha256:source.sourceSha256,
          planSha256:source.planSha256,toolchainProfile,
          project:source.project,artifact:saved,evidence,finishedAt:Date.now(),
          gradle:{exitCode:0,timedOut:false,tasks:plan.build.tasks},
          toolchain:{profile:toolchainProfile,executionHost:tools.executionHost||null},
          deviceAcceptance:'NOT_RUN',installed:false,deviceVerified:false,productionReady:false};
        const verification=verifyOwnedProjectReceipt(result,{plan,sourceSha256:source.sourceSha256,
          project:source.project,artifactRoot:state});
        if(verification.verdict!=='CONFIRMED')return finish({status:'FAILED_BUILD_EVIDENCE'});
        return finish({...result,verification});
      } finally {
        try { fs.rmSync(staged,{force:true}); } catch { /* explicit artifact verification still mandatory */ }
      }

    } catch {return finish({status:!releaseAllowed?'BUILD_TERMINATION_UNCONFIRMED':signal?.aborted?'BUILD_CANCELLED':'FAILED_BUILD_IO'});}
    finally {if(releaseAllowed)release();}
}
async function main() {
  const [command,...args]=process.argv.slice(2);
  if (!['create','verify','build','status'].includes(command))throw Error('USAGE: create|verify|build|status [--recipe m4-notes|m4-counter] [--host-sdk]');
  let recipeId=RECIPE.id, toolchainProfile='termux-arm64',seenRecipe=false,seenHost=false;
  for(let i=0;i<args.length;i++) {
    if(args[i]==='--recipe'&&!seenRecipe&&args[i+1]) {recipeId=args[++i];seenRecipe=true;}
    else if(args[i]==='--host-sdk'&&command==='build'&&!seenHost) {toolchainProfile='host-sdk';seenHost=true;}
    else throw Error('UNSUPPORTED_PROJECT_ARGUMENT');
  }
  getProjectPlan(recipeId);
  const controller=new AbortController(),abort=()=>controller.abort();
  process.once('SIGINT',abort);process.once('SIGTERM',abort);
  const options={recipeId,toolchainProfile,signal:controller.signal};
  let report;
  try {
    report=command==='create'?createOwnedApp(options):command==='verify'?verifyOwnedApp(options):
      command==='status'?readOwnedAppStatus(options):await buildOwnedApp(options);
  } finally {process.removeListener('SIGINT',abort);process.removeListener('SIGTERM',abort);}
  process.stdout.write(JSON.stringify(report)+'\n');
  if(!['PROJECT_CREATED_NOT_BUILT','SOURCE_VERIFIED','EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED',
    'PROJECT_BUILD_STATUS','NO_PROJECT_BUILD_REPORT'].includes(report.status))process.exitCode=2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))
    main().catch(error=>{process.stderr.write('Continuum M4: '+String(error.message).slice(0,240)+'\n');process.exitCode=2;});
