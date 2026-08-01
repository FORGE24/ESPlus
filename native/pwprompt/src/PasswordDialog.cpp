#include "PasswordDialog.h"

#include <QDialogButtonBox>
#include <QFormLayout>
#include <QLabel>
#include <QMessageBox>
#include <QVBoxLayout>

PasswordDialog::PasswordDialog(const QString &title,
                               const QString &prompt,
                               Mode mode,
                               QWidget *parent)
    : QDialog(parent), mode_(mode) {
    setWindowTitle(title);
    setModal(true);
    setWindowFlag(Qt::WindowStaysOnTopHint, true);
    setMinimumWidth(420);

    auto *layout = new QVBoxLayout(this);
    layout->addWidget(new QLabel(prompt, this));

    auto *form = new QFormLayout();
    passwordEdit_ = new QLineEdit(this);
    passwordEdit_->setEchoMode(QLineEdit::Password);
    passwordEdit_->setMinimumHeight(32);
    form->addRow(tr("Password"), passwordEdit_);

    if (mode_ == Mode::Confirm) {
        confirmEdit_ = new QLineEdit(this);
        confirmEdit_->setEchoMode(QLineEdit::Password);
        confirmEdit_->setMinimumHeight(32);
        form->addRow(tr("Confirm"), confirmEdit_);
    }
    layout->addLayout(form);

    auto *buttons = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel, this);
    layout->addWidget(buttons);
    connect(buttons, &QDialogButtonBox::accepted, this, &PasswordDialog::onAccept);
    connect(buttons, &QDialogButtonBox::rejected, this, &QDialog::reject);

    passwordEdit_->setFocus();
}

QString PasswordDialog::password() const {
    return passwordEdit_ ? passwordEdit_->text() : QString();
}

void PasswordDialog::onAccept() {
    if (password().isEmpty()) {
        QMessageBox::warning(this, windowTitle(), tr("Password cannot be empty."));
        return;
    }
    if (mode_ == Mode::Confirm) {
        if (confirmEdit_ == nullptr || password() != confirmEdit_->text()) {
            QMessageBox::warning(this, windowTitle(), tr("Passwords do not match."));
            return;
        }
    }
    accept();
}
