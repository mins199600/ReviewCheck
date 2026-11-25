// 면접관 답변(마크다운)을 HTML로 바꿔서 보여준다. 라이브러리를 못 불러오면 글자 그대로 둔다.
if (window.marked && window.DOMPurify) {
  document.querySelectorAll('.bubble.md').forEach(el => {
    el.innerHTML = DOMPurify.sanitize(marked.parse(el.textContent));
  });
} else {
  document.querySelectorAll('.bubble.md').forEach(el => { el.style.whiteSpace = 'pre-wrap'; });
}

// AI 응답을 기다리는 동안 버튼을 막고 안내 문구를 보여준다.
document.querySelectorAll('form[data-loading]').forEach(form => {
  form.addEventListener('submit', () => {
    const button = form.querySelector('button[type=submit]');
    if (button) button.disabled = true;
    const scope = form.closest('.card') || form;
    scope.querySelectorAll('.loading').forEach(el => el.classList.add('on'));
    scope.querySelectorAll('.idle').forEach(el => { el.style.display = 'none'; });
  });
  const textarea = form.querySelector('textarea');
  if (textarea) {
    textarea.addEventListener('keydown', e => {
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        form.requestSubmit();
      }
    });
  }
});

// 대화가 길어지면 최신 질문이 보이도록 아래로 스크롤
const answerBox = document.querySelector('.answer-box');
if (answerBox && document.querySelectorAll('.turn').length > 1) {
  answerBox.scrollIntoView({ block: 'end' });
}

// 눈 아이콘을 누르면 접속 코드를 보였다 가렸다 한다.
document.querySelectorAll('.toggle-code').forEach(button => {
  button.addEventListener('click', () => {
    const input = document.getElementById(button.dataset.target);
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.classList.toggle('showing', show);
    button.setAttribute('aria-label', show ? '접속 코드 숨기기' : '접속 코드 보기');
    input.focus();
  });
});
