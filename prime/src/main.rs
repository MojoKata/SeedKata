mod bip39;
mod engine;
mod sha256;
mod theme;

use std::cell::RefCell;
use std::rc::Rc;

use slint_keyos_platform::app_ui2;
use slint_keyos_platform::slint::{ComponentHandle, ModelRc, SharedString, VecModel};

app_ui2!("DiceSeed");

const CANDIDATES: usize = 6;

#[derive(Default)]
struct Session {
    engine: Option<engine::Engine>,
    /// Set when leaving the rolling screen is intentional (Continue pressed
    /// or discard confirmed), so back-navigation interception stands down.
    leave_authorized: bool,
    /// Final mnemonic word indices once rolling completes.
    word_indices: Vec<u16>,
    /// Verification: word positions (0-based) in the random order asked.
    verify_order: Vec<usize>,
    verify_step: usize,
    correct_pos: usize,
    wrong: [bool; CANDIDATES],
}

fn rand_u32() -> u32 {
    let mut b = [0u8; 4];
    getrandom::getrandom(&mut b).expect("rng");
    u32::from_le_bytes(b)
}

/// Uniform-enough index for UI shuffling (never used for seed entropy).
fn rand_below(n: usize) -> usize {
    (rand_u32() as usize) % n
}

fn shuffled(n: usize) -> Vec<usize> {
    let mut v: Vec<usize> = (0..n).collect();
    for i in (1..n).rev() {
        v.swap(i, rand_below(i + 1));
    }
    v
}

fn string_model(items: Vec<String>) -> ModelRc<SharedString> {
    ModelRc::new(VecModel::from(
        items.into_iter().map(SharedString::from).collect::<Vec<_>>(),
    ))
}

fn push_rolling_state(state: &AppState, session: &Session, words: &[&'static str]) {
    let eng = session.engine.as_ref().expect("engine");
    let indices = eng.word_indices();

    state.set_bits_collected(eng.bits_collected() as i32);
    state.set_bits_target(eng.bits_target() as i32);
    state.set_roll_number(eng.rolls as i32 + 1);
    state.set_roll_estimate(eng.estimated_total_rolls() as i32);
    state.set_current_word(eng.current_word_number() as i32);
    state.set_word_progress(eng.current_word_progress());
    state.set_words_so_far(SharedString::from(
        indices
            .iter()
            .enumerate()
            .map(|(i, &w)| format!("{}. {}", i + 1, words[w as usize]))
            .collect::<Vec<_>>()
            .join("   "),
    ));
    state.set_rolling_complete(eng.is_complete());
}

fn push_verify_candidates(state: &AppState, session: &mut Session, words: &[&'static str]) {
    let word_pos = session.verify_order[session.verify_step];
    let correct = session.word_indices[word_pos];

    // Five distinct decoys, then the correct word at a random slot (req 4.2).
    let mut cand: Vec<u16> = Vec::with_capacity(CANDIDATES);
    while cand.len() < CANDIDATES - 1 {
        let d = rand_below(words.len()) as u16;
        if d != correct && !cand.contains(&d) {
            cand.push(d);
        }
    }
    session.correct_pos = rand_below(CANDIDATES);
    cand.insert(session.correct_pos, correct);
    session.wrong = [false; CANDIDATES];

    state.set_verify_word_number(word_pos as i32 + 1);
    state.set_verify_step(session.verify_step as i32 + 1);
    state.set_verify_candidates(string_model(
        cand.iter().map(|&i| words[i as usize].to_string()).collect(),
    ));
    state.set_verify_wrong(ModelRc::new(VecModel::from(vec![false; CANDIDATES])));
}

fn app_main(cx: AppContext, ui: AppWindow) {
    log_server::init_wait(env!("CARGO_CRATE_NAME")).unwrap();
    log::set_max_level(log::LevelFilter::Info);

    theme::init(&ui);

    let words: &'static [&'static str] = Box::leak(bip39::wordlist().into_boxed_slice());
    let session = Rc::new(RefCell::new(Session::default()));

    let state = ui.global::<AppState>();

    {
        let ui_weak = ui.as_weak();
        let session = session.clone();
        state.on_begin_rolling(move || {
            let ui = ui_weak.unwrap();
            let state = ui.global::<AppState>();
            let mut s = session.borrow_mut();
            let mode = match state.get_quality() {
                0 => engine::Mode::Trust,
                1 => engine::Mode::Hash,
                2 => engine::Mode::ModSum,
                _ => engine::Mode::Pairwise,
            };
            s.engine = Some(engine::Engine::new(
                state.get_word_count() as usize,
                state.get_die_sides() as u8,
                mode,
            ));
            s.word_indices.clear();
            s.leave_authorized = false;
            state.set_cancel_dialog_open(false);
            state.set_words(string_model(vec![]));
            push_rolling_state(&state, &s, words);
        });
    }

    {
        let ui_weak = ui.as_weak();
        let session = session.clone();
        state.on_roll(move |face| {
            let ui = ui_weak.unwrap();
            let state = ui.global::<AppState>();
            let mut s = session.borrow_mut();
            let Some(eng) = s.engine.as_mut() else {
                return;
            };
            if eng.is_complete() {
                return;
            }
            eng.roll(face as u8);
            if eng.is_complete() {
                let indices = eng.word_indices();
                state.set_words(string_model(
                    indices.iter().map(|&i| words[i as usize].to_string()).collect(),
                ));
                s.word_indices = indices;
            }
            push_rolling_state(&state, &s, words);
        });
    }

    {
        let ui_weak = ui.as_weak();
        let session = session.clone();
        state.on_undo_roll(move || {
            let ui = ui_weak.unwrap();
            let state = ui.global::<AppState>();
            let mut s = session.borrow_mut();
            let complete = {
                let Some(eng) = s.engine.as_mut() else {
                    return;
                };
                eng.undo();
                eng.is_complete()
            };
            if !complete {
                s.word_indices.clear();
            }
            push_rolling_state(&state, &s, words);
        });
    }

    {
        let session = session.clone();
        state.on_cancel_rolling(move || {
            let mut s = session.borrow_mut();
            s.engine = None;
            s.leave_authorized = true;
        });
    }

    {
        let session = session.clone();
        state.on_leave_rolling(move || {
            session.borrow_mut().leave_authorized = true;
        });
    }

    // Requirement 2.7: the device back button (and back gesture) both land in
    // the router's backward navigation, which offers no veto hook. Instead,
    // when the rolling screen is left with rolls at stake and without
    // authorization, immediately restore the route and open the cancel
    // confirmation.
    {
        let ui_weak = ui.as_weak();
        let session = session.clone();
        let router = cx.router;
        let last_route = Rc::new(RefCell::new(String::new()));
        cx.router.with(|r| {
            r.register_on_navigation_end(move |history| {
                let now = history.get_current_path().unwrap_or("").to_string();
                let prev = last_route.replace(now.clone());
                if prev != "/rolling" || now == "/rolling" {
                    return;
                }
                let pending = {
                    let s = session.borrow();
                    !s.leave_authorized && s.engine.as_ref().is_some_and(|e| e.rolls > 0)
                };
                if !pending {
                    return;
                }
                let ui_weak = ui_weak.clone();
                // Deferred: the hook runs inside the router borrow.
                slint_keyos_platform::slint::Timer::single_shot(
                    std::time::Duration::from_millis(0),
                    move || {
                        router.with(|r| {
                            let _ = r.push_raw_route("/rolling".to_string());
                        });
                        if let Some(ui) = ui_weak.upgrade() {
                            ui.global::<AppState>().set_cancel_dialog_open(true);
                        }
                    },
                );
            });
        });
    }

    {
        let ui_weak = ui.as_weak();
        let session = session.clone();
        state.on_begin_verify(move || {
            let ui = ui_weak.unwrap();
            let state = ui.global::<AppState>();
            let mut s = session.borrow_mut();
            let n = s.word_indices.len();
            if n == 0 {
                return;
            }
            s.verify_order = shuffled(n);
            s.verify_step = 0;
            state.set_verify_complete(false);
            push_verify_candidates(&state, &mut s, words);
        });
    }

    {
        let ui_weak = ui.as_weak();
        let session = session.clone();
        state.on_verify_select(move |idx| {
            let ui = ui_weak.unwrap();
            let state = ui.global::<AppState>();
            let mut s = session.borrow_mut();
            if state.get_verify_complete() || s.verify_order.is_empty() {
                return;
            }
            let idx = idx as usize;
            if idx == s.correct_pos {
                s.verify_step += 1;
                if s.verify_step == s.verify_order.len() {
                    state.set_verify_step(s.verify_step as i32);
                    state.set_verify_complete(true);
                } else {
                    push_verify_candidates(&state, &mut s, words);
                }
            } else if idx < CANDIDATES && !s.wrong[idx] {
                s.wrong[idx] = true;
                state.set_verify_wrong(ModelRc::new(VecModel::from(s.wrong.to_vec())));
            }
        });
    }

    ui.run().expect("UI running");
}
