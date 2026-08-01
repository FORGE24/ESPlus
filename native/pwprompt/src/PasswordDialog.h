#pragma once

#include <QDialog>
#include <QLineEdit>
#include <QString>

class PasswordDialog : public QDialog {
    Q_OBJECT
public:
    enum class Mode {
        Single,
        Confirm
    };

    explicit PasswordDialog(const QString &title,
                            const QString &prompt,
                            Mode mode,
                            QWidget *parent = nullptr);

    QString password() const;

private:
    Mode mode_;
    QLineEdit *passwordEdit_ = nullptr;
    QLineEdit *confirmEdit_ = nullptr;

private slots:
    void onAccept();
};
