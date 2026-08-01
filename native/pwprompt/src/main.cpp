#include "PasswordDialog.h"

#include <QApplication>
#include <QCommandLineOption>
#include <QCommandLineParser>
#include <QByteArray>
#include <QTextStream>
#include <cstdio>

#ifdef Q_OS_WIN
#  include <windows.h>
#  include <io.h>
#  include <fcntl.h>
#endif

static void attachParentConsoleIo() {
#ifdef Q_OS_WIN
    // When launched with redirected stdout by Java, keep writing to that pipe.
    // Also re-open CRT stdout if needed.
    _setmode(_fileno(stdout), _O_BINARY);
#endif
}

int main(int argc, char *argv[]) {
    attachParentConsoleIo();

    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("seminecraft-pwprompt"));
    QApplication::setOrganizationName(QStringLiteral("SEMinecraft"));

    QCommandLineParser parser;
    parser.setApplicationDescription(QStringLiteral("SEMinecraft isolated password prompt (Qt6)"));
    parser.addHelpOption();

    QCommandLineOption titleOpt({QStringLiteral("t"), QStringLiteral("title")},
                                QStringLiteral("Window title"),
                                QStringLiteral("title"),
                                QStringLiteral("SEMinecraft"));
    QCommandLineOption promptOpt({QStringLiteral("p"), QStringLiteral("prompt")},
                                 QStringLiteral("Prompt text"),
                                 QStringLiteral("prompt"),
                                 QStringLiteral("Enter password"));
    QCommandLineOption modeOpt({QStringLiteral("m"), QStringLiteral("mode")},
                               QStringLiteral("single | confirm"),
                               QStringLiteral("mode"),
                               QStringLiteral("single"));
    parser.addOption(titleOpt);
    parser.addOption(promptOpt);
    parser.addOption(modeOpt);
    parser.process(app);

    const QString modeText = parser.value(modeOpt).trimmed().toLower();
    PasswordDialog::Mode mode = PasswordDialog::Mode::Single;
    if (modeText == QLatin1String("confirm")) {
        mode = PasswordDialog::Mode::Confirm;
    }

    PasswordDialog dialog(parser.value(titleOpt), parser.value(promptOpt), mode);
    const int result = dialog.exec();
    if (result != QDialog::Accepted) {
        return 2; // canceled
    }

    const QByteArray utf8 = dialog.password().toUtf8();
    const QByteArray b64 = utf8.toBase64(QByteArray::Base64Encoding | QByteArray::OmitTrailingEquals);
    // One-line Base64 payload — never put password into argv or logs.
    fwrite(b64.constData(), 1, static_cast<size_t>(b64.size()), stdout);
    fputc('\n', stdout);
    fflush(stdout);

    // Best-effort wipe of QString buffers is limited; dialog is destroyed next.
    return 0;
}
