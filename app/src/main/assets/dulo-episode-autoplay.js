(() => {
    if (location.origin !== 'https://dulo.mov' || window.__eminenceEpisodeAutoplay) return;

    const state = {active: false};
    window.__eminenceEpisodeAutoplay = state;
    const handled = new WeakMap();

    // The media ended event does not bubble. Capture it even if Dulo replaces the player.
    document.addEventListener('ended', (event) => {
        const video = event.target;
        const episode = location.pathname + location.search;
        if (!state.active || !/^\/watch\/tv\/\d+\/?$/.test(location.pathname)
                || !(video instanceof HTMLVideoElement) || !video.ended
                || !Number.isFinite(video.duration) || video.duration <= 0
                || video.currentTime < video.duration - 1 || handled.get(video) === episode) return;

        handled.set(video, episode);
        // Give Dulo's own end-of-playback handler priority if it advances the episode.
        setTimeout(() => {
            if (!state.active || !video.isConnected || !video.ended
                    || location.pathname + location.search !== episode) return;

            const next = Array.from(document.querySelectorAll('button')).find((button) =>
                !button.disabled && button.getClientRects().length > 0
                && button.getAttribute('aria-disabled') !== 'true'
                && [button.getAttribute('aria-label'), button.title, button.textContent].some(
                    (label) => label && label.trim().toLowerCase() === 'next episode'));
            if (next) next.click();
        }, 700);
    }, true);

    document.addEventListener('playing', (event) => {
        const video = event.target;
        if (video instanceof HTMLVideoElement && !video.ended) handled.delete(video);
    }, true);
})();
