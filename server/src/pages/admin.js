export const ADMIN_HTML = `<!doctype html>
<html lang="uk">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Адміністрування — Karaoke Studio</title>
<style nonce="__NONCE__">
*{box-sizing:border-box}
body{margin:0;background:#0e0b1a;color:#e8e6f2;font:15px/1.5 system-ui,sans-serif}
header{display:flex;gap:12px;align-items:center;padding:12px 20px;background:#1f1a38}
header h1{font-size:18px;margin:0;flex:1}
main{max-width:1100px;margin:0 auto;padding:20px}
nav{display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap}
button,input,select,textarea{font:inherit;color:inherit;background:#2a2450;border:1px solid #433b73;border-radius:8px;padding:8px 12px}
button{cursor:pointer;background:#6d4fe0;border-color:#6d4fe0}
button.secondary{background:#2a2450;border-color:#433b73}
button.danger{background:#7a2640;border-color:#7a2640}
button:disabled{opacity:.5;cursor:default}
table{width:100%;border-collapse:collapse}
th,td{text-align:left;padding:8px;border-bottom:1px solid #2c2650;vertical-align:top}
th{color:#b9a2ff;font-weight:600}
.card{background:#1f1a38;border-radius:12px;padding:16px;margin-bottom:16px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}
.stat b{display:block;font-size:24px}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}
.error{color:#ff8da1}
.muted{color:#9b97b3}
form.edit{display:grid;gap:8px}
textarea{min-height:140px;width:100%}
pre{white-space:pre-wrap;font-size:13px;margin:0}
.hidden{display:none}
</style>
</head>
<body>
<header>
<h1>Karaoke Studio — адміністрування</h1>
<span id="who" class="muted"></span>
<button id="logout" class="secondary hidden">Вийти</button>
</header>
<main>
<section id="login" class="card">
<h2>Вхід</h2>
<form id="login-form" class="edit">
<input id="login-user" placeholder="Ім’я користувача" autocomplete="username" required>
<input id="login-pass" type="password" placeholder="Пароль" autocomplete="current-password" required>
<button type="submit">Увійти</button>
<div id="login-error" class="error"></div>
</form>
</section>
<section id="app" class="hidden">
<nav id="tabs"></nav>
<div id="view"></div>
</section>
</main>
<script nonce="__NONCE__">
(function () {
  var session = null;
  var currentTab = 'stats';
  var tabs = [['stats', 'Статистика'], ['songs', 'Пісні'], ['users', 'Користувачі'], ['logs', 'Журнал']];
  var $ = function (id) { return document.getElementById(id); };

  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (key) {
      if (key === 'text') node.textContent = attrs[key];
      else if (key.indexOf('on') === 0) node.addEventListener(key.slice(2), attrs[key]);
      else node.setAttribute(key, attrs[key]);
    });
    (children || []).forEach(function (child) { if (child) node.appendChild(child); });
    return node;
  }

  function api(path, options, retried) {
    var init = options || {};
    var headers = { Authorization: 'Bearer ' + session.token };
    if (init.body !== undefined) headers['Content-Type'] = 'application/json';
    return fetch(path, { method: init.method || 'GET', headers: headers, body: init.body === undefined ? undefined : JSON.stringify(init.body) })
      .then(function (response) {
        if (response.status === 401 && !retried) {
          return refresh().then(function () { return api(path, options, true); });
        }
        if (response.status === 204) return null;
        return response.json().then(function (data) {
          if (!response.ok) throw new Error(data && data.error ? data.error.message : 'Помилка ' + response.status);
          return data;
        });
      });
  }

  function refresh() {
    return fetch('/api/v1/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: session.refreshToken }) })
      .then(function (response) {
        if (!response.ok) { signOut(); throw new Error('Сесію завершено'); }
        return response.json();
      })
      .then(function (data) { session = data; });
  }

  function signOut() {
    session = null;
    $('app').classList.add('hidden');
    $('login').classList.remove('hidden');
    $('logout').classList.add('hidden');
    $('who').textContent = '';
  }

  function renderTabs() {
    var nav = $('tabs');
    nav.textContent = '';
    tabs.forEach(function (tab) {
      nav.appendChild(el('button', { class: tab[0] === currentTab ? '' : 'secondary', text: tab[1], onclick: function () { currentTab = tab[0]; show(); } }));
    });
  }

  function show() {
    renderTabs();
    var view = $('view');
    view.textContent = '';
    var loaders = { stats: showStats, songs: showSongs, users: showUsers, logs: showLogs };
    loaders[currentTab](view).catch(function (error) { view.appendChild(el('p', { class: 'error', text: error.message })); });
  }

  function showStats(view) {
    return api('/api/v1/admin/stats').then(function (stats) {
      var grid = el('div', { class: 'grid' });
      [['Користувачі', stats.users], ['Пісні', stats.songs], ['Видалені пісні', stats.deletedSongs], ['Виконання', stats.performances],
        ['Обране', stats.favorites], ['Записи голосу', stats.recordings.files], ['Розмір записів, КБ', Math.round(stats.recordings.bytes / 1024)],
        ['Розмір бази, КБ', Math.round(stats.database.sizeBytes / 1024)], ['Активні сесії', stats.activeSessions], ['Працює, с', stats.uptimeSec]]
        .forEach(function (item) {
          grid.appendChild(el('div', { class: 'card stat' }, [el('span', { class: 'muted', text: item[0] }), el('b', { text: String(item[1]) })]));
        });
      view.appendChild(el('p', { class: 'muted', text: 'Версія сервера: ' + stats.version }));
      view.appendChild(grid);
    });
  }

  function showUsers(view) {
    return api('/api/v1/admin/users?limit=200').then(function (data) {
      var table = el('table', {}, [el('tr', {}, ['ID', 'Ім’я', 'Нікнейм', 'Роль', 'Виконань', 'Обраних'].map(function (name) { return el('th', { text: name }); }))]);
      data.items.forEach(function (user) {
        table.appendChild(el('tr', {}, [user.id, user.username, user.nickname, user.role, user.performances, user.favorites].map(function (value) { return el('td', { text: String(value) }); })));
      });
      view.appendChild(el('p', { class: 'muted', text: 'Усього користувачів: ' + data.total }));
      view.appendChild(table);
    });
  }

  function showLogs(view) {
    var level = el('select', {}, ['debug', 'info', 'warn', 'error'].map(function (name) { return el('option', { value: name, text: name }); }));
    level.value = 'info';
    var box = el('div');
    function load() {
      box.textContent = '';
      return api('/api/v1/admin/logs?limit=200&level=' + level.value).then(function (data) {
        data.items.forEach(function (entry) {
          var line = entry.time + '  ' + entry.level.toUpperCase() + '  ' + entry.message + '  ' + JSON.stringify(entry, function (key, value) { return ['time', 'level', 'message'].indexOf(key) >= 0 ? undefined : value; });
          box.appendChild(el('pre', { class: entry.level === 'error' ? 'error' : '', text: line }));
        });
      }).catch(function (error) { box.appendChild(el('p', { class: 'error', text: error.message })); });
    }
    level.addEventListener('change', load);
    view.appendChild(el('div', { class: 'row' }, [level, el('button', { class: 'secondary', text: 'Оновити', onclick: load })]));
    view.appendChild(box);
    return load();
  }

  function songForm(song, onSaved) {
    var isNew = !song;
    var data = song || { slug: '', title: '', artist: '', durationMs: 60000, genre: 'Інше', audioSource: 'demo:', coverUrl: '', lyrics: '' };
    var fields = {};
    var form = el('form', { class: 'edit card' });
    var error = el('div', { class: 'error' });
    [['slug', 'slug (латиниця, цифри, дефіс)'], ['title', 'Назва'], ['artist', 'Виконавець'], ['durationMs', 'Тривалість, мс'], ['genre', 'Жанр'], ['audioSource', 'Аудіо (demo:slug або https://…)'], ['coverUrl', 'Обкладинка (https://…)']].forEach(function (item) {
      var input = el('input', { placeholder: item[1], title: item[1] });
      input.value = data[item[0]] == null ? '' : data[item[0]];
      if (item[0] === 'slug' && !isNew) input.disabled = true;
      fields[item[0]] = input;
      form.appendChild(input);
    });
    var lyrics = el('textarea', { placeholder: 'Текст у форматі LRC' });
    lyrics.value = data.lyrics || '';
    form.appendChild(lyrics);
    form.appendChild(el('div', { class: 'row' }, [
      el('button', { type: 'submit', text: isNew ? 'Створити' : 'Зберегти' }),
      el('button', { type: 'button', class: 'secondary', text: 'Скасувати', onclick: function () { onSaved(); } })
    ]));
    form.appendChild(error);
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      var body = {
        slug: fields.slug.value.trim(), title: fields.title.value, artist: fields.artist.value,
        durationMs: Number(fields.durationMs.value), genre: fields.genre.value || 'Інше',
        audioSource: fields.audioSource.value.trim(), coverUrl: fields.coverUrl.value.trim() || null, lyrics: lyrics.value
      };
      var request = isNew ? api('/api/v1/songs', { method: 'POST', body: body }) : api('/api/v1/songs/' + encodeURIComponent(data.slug), { method: 'PUT', body: body });
      request.then(function () { onSaved(true); }).catch(function (failure) { error.textContent = failure.message; });
    });
    return form;
  }

  function showSongs(view) {
    var query = el('input', { placeholder: 'Пошук' });
    var deleted = el('input', { type: 'checkbox', id: 'show-deleted' });
    var holder = el('div');
    var list = el('div');
    function load() {
      list.textContent = '';
      return api('/api/v1/admin/songs?limit=200&includeDeleted=' + deleted.checked + '&q=' + encodeURIComponent(query.value)).then(function (data) {
        var table = el('table', {}, [el('tr', {}, ['slug', 'Назва', 'Виконавець', 'Жанр', 'Тривалість', 'Стан', ''].map(function (name) { return el('th', { text: name }); }))]);
        data.items.forEach(function (song) {
          var actions = el('td', {});
          actions.appendChild(el('button', { class: 'secondary', text: song.deleted ? 'Відновити' : 'Редагувати', onclick: function () { edit(song.slug, song.deleted); } }));
          if (!song.deleted) {
            actions.appendChild(document.createTextNode(' '));
            actions.appendChild(el('button', { class: 'danger', text: 'Видалити', onclick: function () {
              if (window.confirm('Видалити пісню ' + song.slug + '?')) api('/api/v1/songs/' + encodeURIComponent(song.slug), { method: 'DELETE' }).then(load).catch(function (failure) { window.alert(failure.message); });
            } }));
          }
          table.appendChild(el('tr', {}, [
            el('td', { text: song.slug }), el('td', { text: song.title || '' }), el('td', { text: song.artist || '' }), el('td', { text: song.genre || '' }),
            el('td', { text: Math.round(song.durationMs / 1000) + ' с' }), el('td', { text: song.deleted ? 'видалена' : 'активна' }), actions
          ]));
        });
        list.appendChild(table);
      }).catch(function (error) { list.appendChild(el('p', { class: 'error', text: error.message })); });
    }
    function edit(slug, wasDeleted) {
      api('/api/v1/admin/songs/' + encodeURIComponent(slug)).then(function (song) {
        holder.textContent = '';
        holder.appendChild(songForm(song, function (changed) { holder.textContent = ''; if (changed) load(); }));
        if (wasDeleted) holder.insertBefore(el('p', { class: 'muted', text: 'Збереження поверне пісню в каталог.' }), holder.firstChild);
      }).catch(function (error) { window.alert(error.message); });
    }
    query.addEventListener('input', load);
    deleted.addEventListener('change', load);
    view.appendChild(el('div', { class: 'row' }, [
      query,
      el('label', {}, [deleted, document.createTextNode(' показувати видалені')]),
      el('button', { text: 'Нова пісня', onclick: function () { holder.textContent = ''; holder.appendChild(songForm(null, function (changed) { holder.textContent = ''; if (changed) load(); })); } })
    ]));
    view.appendChild(holder);
    view.appendChild(list);
    return load();
  }

  $('login-form').addEventListener('submit', function (event) {
    event.preventDefault();
    $('login-error').textContent = '';
    fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: $('login-user').value, password: $('login-pass').value }) })
      .then(function (response) { return response.json().then(function (data) { return { ok: response.ok, data: data }; }); })
      .then(function (result) {
        if (!result.ok) throw new Error(result.data.error ? result.data.error.message : 'Не вдалося увійти');
        if (result.data.user.role !== 'admin') throw new Error('Потрібні права адміністратора');
        session = result.data;
        $('login-pass').value = '';
        $('login').classList.add('hidden');
        $('app').classList.remove('hidden');
        $('logout').classList.remove('hidden');
        $('who').textContent = session.user.nickname;
        show();
      })
      .catch(function (error) { $('login-error').textContent = error.message; });
  });

  $('logout').addEventListener('click', function () {
    if (session) fetch('/api/v1/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: session.refreshToken }) });
    signOut();
  });
})();
</script>
</body>
</html>
`;
