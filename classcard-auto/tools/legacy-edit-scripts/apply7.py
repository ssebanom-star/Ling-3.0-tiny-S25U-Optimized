import re
exec(open('/tmp/claude-0/work/fix.py').read())
P = D + 'BotService.java'
s = open(P).read()
s = s.replace('''    private Button overlayGearButton;
    private Button overlayOffButton;
    private LinearLayout overlayTools;
    private LinearLayout overlayPanel;
    private boolean overlayDismissed;
''', '''    private FloatingPanel floating;
    private boolean overlayDismissed;
    private boolean dragging;
    private int dragStartX;
    private int dragStartY;
''', 1)
open(P, 'w').write(s)

for sig in ['private final void rebuildOverlay()', 'private static Button overlayToolButton(BotService botService, String label, int color)',
            'private final void addSeekRow(LinearLayout panel, float f, String label, final int min, int max, final int step, int current, final Function1<Integer, Unit> onChange)']:
    replace(P, sig, '')

replace(P, 'private final void createOverlay()', '''    private final void createOverlay() {
        final float f = getResources().getDisplayMetrics().density;
        FloatingPanel panel = new FloatingPanel(this, getStorage().getPrefs(), new FloatingPanel.Host() {
            @Override
            public void onPrimary() {
                BotService.createOverlay$lambda$32$lambda$31(BotService.this, null);
            }

            @Override
            public void onHide() {
                if (BotService.this.busy) {
                    return;
                }
                if (BotService.this.getEngine().getBeforeTest()) {
                    BotService.this.overlayDismissed = true;
                    BotService.this.refreshOverlay();
                }
            }

            @Override
            public void onDrag(float dx, float dy, boolean finished) {
                BotService.this.dragOverlay(dx, dy, finished);
            }

            @Override
            public boolean isBlocked() {
                return BotService.this.overlayInputGuard.getBlocked() || BotService.this.busy;
            }
        });
        panel.setVisibility(8);
        this.overlayText = panel.getStatusText();
        this.overlayButton = panel.getPrimary();
        this.floating = panel;
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-2, -2, 2032, 40, -3);
        layoutParams.gravity = 8388661;
        layoutParams.x = createOverlay$dp(f, 8);
        layoutParams.y = createOverlay$dp(f, 40);
        Object systemService = getSystemService("window");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager");
        ((WindowManager) systemService).addView(panel, layoutParams);
        this.overlay = panel;
        this.overlayParams = layoutParams;
    }

    private final void dragOverlay(float dx, float dy, boolean finished) {
        WindowManager.LayoutParams layoutParams = this.overlayParams;
        LinearLayout view = this.overlay;
        if (layoutParams == null || view == null) {
            return;
        }
        if (!this.dragging) {
            this.dragging = true;
            this.dragStartX = layoutParams.x;
            this.dragStartY = layoutParams.y;
        }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxX = Math.max(0, metrics.widthPixels - view.getWidth());
        int maxY = Math.max(0, metrics.heightPixels - view.getHeight());
        // gravity is top|end, so x counts from the right edge: dragging right shrinks it
        layoutParams.x = Math.max(0, Math.min(maxX, (int) (this.dragStartX - dx)));
        layoutParams.y = Math.max(0, Math.min(maxY, (int) (this.dragStartY + dy)));
        Object systemService = getSystemService("window");
        ((WindowManager) systemService).updateViewLayout(view, layoutParams);
        if (finished) {
            this.dragging = false;
        }
    }''')

replace(P, 'public final void refreshOverlay()', '''    public final void refreshOverlay() {
        if (this.engine == null) {
            return;
        }
        boolean active = getEngine().getActive();
        boolean beforeTest = getEngine().getBeforeTest();
        boolean collecting = getEngine().getCollecting();
        if (!active) {
            // 실행이 끝나면 꺼 두었던 UI를 다시 쓸 수 있게 되돌린다.
            this.overlayDismissed = false;
        }
        FloatingPanel panel = this.floating;
        if (panel != null) {
            panel.render(active, this.status, !active || collecting, beforeTest);
        }
        LinearLayout linearLayout = this.overlay;
        if (linearLayout != null) {
            linearLayout.setVisibility((UsageConsent.INSTANCE.getAllowed() && (active || this.currentStartAllowed) && !this.overlayDismissed) ? 0 : 8);
        }
        WindowManager.LayoutParams layoutParams = this.overlayParams;
        if (layoutParams != null) {
            int i = active ? layoutParams.flags | Uuid.SIZE_BITS : layoutParams.flags & (-129);
            if (i != layoutParams.flags) {
                layoutParams.flags = i;
                LinearLayout linearLayout2 = this.overlay;
                if (linearLayout2 != null) {
                    Object systemService = getSystemService("window");
                    Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager");
                    ((WindowManager) systemService).updateViewLayout(linearLayout2, layoutParams);
                }
            }
        }
    }''')
print('ok')
